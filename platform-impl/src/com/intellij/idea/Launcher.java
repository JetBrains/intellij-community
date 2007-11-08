package com.intellij.idea;

import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 4, 2005
 * Time: 10:06:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class Launcher {
    @SuppressWarnings({"HardCodedStringLiteral"})
    public static void main(String[] args) throws InterruptedException {
        String javaVmExecutablePath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        try {
            final Process process = Runtime.getRuntime().exec("cmd " + javaVmExecutablePath + " -cp " + classpath + " com.intellij.idea.Main");
            new Thread(new Redirector(process.getErrorStream(), System.err)).start();
            new Thread(new Redirector(process.getInputStream(), System.out)).start();
            process.waitFor();
        } catch (IOException e) {
            System.out.println("Can't launch java VM executable: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("Launcher exited");
    }

    private static class Redirector implements Runnable {
        private InputStream myInput;
        private PrintStream myOutput;

        public Redirector(InputStream input, PrintStream output) {
            myInput = input;
            myOutput = output;
        }

        public void run() {
            Reader reader = new InputStreamReader(myInput);
            do {
                int ch = 0;
                try {
                    ch = reader.read();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (ch == -1) break;
                myOutput.print((char) ch);
            } while (true);
        }
    }
}
