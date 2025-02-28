package name.ncg777.wavpermuter;

import javax.sound.sampled.*;
import java.io.*;
import java.util.*;

public class WavPermuter {
    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println("Usage: java WavPermuter <input.wav> <bpm> <offset-ms> <n> <p-sequence> <output.wav>");
            System.exit(1);
        }

        String inputFile = args[0];
        double bpm = Double.parseDouble(args[1]);
        int offsetMs = Integer.parseInt(args[2]);
        int n = Integer.parseInt(args[3]);
        int[] p = Arrays.stream(args[4].split(" ")).mapToInt(Integer::parseInt).toArray();  // Space-separated parsing
        String outputFile = args[5];

        try {
            // Load WAV file
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(new File(inputFile));
            AudioFormat format = audioStream.getFormat();
            byte[] audioBytes = audioStream.readAllBytes();
            audioStream.close();

            // Calculate chunk size (samples per 16th note)
            int bytesPerFrame = format.getFrameSize(); // Includes all channels
            double samplesPerSecond = format.getFrameRate();
            int samplesPerChunk = (int) (samplesPerSecond * (240.0 / (16.0 * bpm)));  // Fixed formula
            int bytesPerChunk = samplesPerChunk * bytesPerFrame;

            // Apply offset
            int offsetBytes = (int) ((offsetMs / 1000.0) * samplesPerSecond) * bytesPerFrame;
            byte[] trimmedAudio = Arrays.copyOfRange(audioBytes, offsetBytes, audioBytes.length);

            // Split into chunks and apply windowing
            List<byte[]> chunks = new ArrayList<>();
            for (int i = 0; i + bytesPerChunk <= trimmedAudio.length; i += bytesPerChunk) {
                byte[] chunk = Arrays.copyOfRange(trimmedAudio, i, i + bytesPerChunk);
                applyWindowFunction(chunk, format);
                chunks.add(chunk);
            }

            // Apply permutation
            List<byte[]> reorderedChunks = new ArrayList<>();
            int newSize = (int)Math.ceil((double)chunks.size()/(double)n)*p.length;
            for(int i=0;i<newSize;i++) reorderedChunks.add(new byte[0]);
            for (int i = 0; i < chunks.size()/n; i++) {
                for (int j = 0; j < p.length; j++) {
                    int targetIndex = i*p.length + j;
                    if (targetIndex < newSize) {
                        reorderedChunks.set(targetIndex, chunks.get((i*n) + p[j]));
                    }
                }
            }
            
            // Remove null gaps (caused by missing permutations)
            reorderedChunks.removeIf(Objects::isNull);

            // Concatenate reordered chunks
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (byte[] chunk : reorderedChunks) {
                outputStream.write(chunk);
            }

            // Save final WAV file
            byte[] finalAudio = outputStream.toByteArray();
            saveWavFile(outputFile, format, finalAudio);

            System.out.println("Processing complete! Output saved as: " + outputFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void applyWindowFunction(byte[] chunk, AudioFormat format) {
        int bytesPerSample = format.getSampleSizeInBits() / 8;
        int numChannels = format.getChannels();
        int frameSize = bytesPerSample * numChannels;
        int sampleCount = chunk.length / frameSize;

        for (int i = 0; i < sampleCount; i++) {
            double windowFactor = windowFunction(i, sampleCount);
            for (int ch = 0; ch < numChannels; ch++) {
                int index = i * frameSize + ch * bytesPerSample;
                int sampleValue = byteArrayToSample(chunk, index, bytesPerSample);
                sampleValue = (int) (sampleValue * windowFactor);
                sampleToByteArray(chunk, index, sampleValue, bytesPerSample);
            }
        }
    }

    private static double windowFunction(int i, int totalSamples) {
        // Hann window function
        return Math.pow(0.5 * (1 - Math.cos(2 * Math.PI * i / (totalSamples - 1))), 1.0/12.0);
    }

    private static int byteArrayToSample(byte[] data, int index, int bytesPerSample) {
        int sample = 0;
        for (int i = 0; i < bytesPerSample; i++) {
            sample |= (data[index + i] & 0xFF) << (8 * i);
        }
        if ((sample & (1 << ((bytesPerSample * 8) - 1))) != 0) {
            sample |= (-1 << (bytesPerSample * 8)); // Sign extension for negative values
        }
        return sample;
    }

    private static void sampleToByteArray(byte[] data, int index, int sample, int bytesPerSample) {
        for (int i = 0; i < bytesPerSample; i++) {
            data[index + i] = (byte) ((sample >> (8 * i)) & 0xFF);
        }
    }

    private static void saveWavFile(String outputFile, AudioFormat format, byte[] audioData) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
             AudioInputStream audioStream = new AudioInputStream(bais, format, audioData.length / format.getFrameSize())) {
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, new File(outputFile));
        }
    }
}
