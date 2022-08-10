package com.intellij.idea;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        writeVmOptionsInFile();
        writeClassPathInFile();
    }

    private static void writeVmOptionsInFile() throws IOException {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> vmOptions = runtimeMxBean.getInputArguments();
        BufferedWriter vmOptionsWriter = new BufferedWriter(new FileWriter("vmOptions.txt"));
        for (String str: vmOptions) {
            vmOptionsWriter.write(str + System.lineSeparator());
        }
        vmOptionsWriter.close();
    }

    private static void writeClassPathInFile() throws IOException {
        String classPath = System.getProperty("java.class.path");
        BufferedWriter classPathWriter = new BufferedWriter(new FileWriter("classPath.txt"));
        classPathWriter.write(classPath + System.lineSeparator());
        classPathWriter.close();
    }
}
