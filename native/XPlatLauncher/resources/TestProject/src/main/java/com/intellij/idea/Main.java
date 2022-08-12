package com.intellij.idea;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        writeVmOptionsInFile();
        writeJavaSystemPropertyInFile("java.class.path");
        writeJavaSystemPropertyInFile("java.home");
        writeJavaSystemPropertyInFile("java.vendor");
        writeJavaSystemPropertyInFile("java.version");
        writeJavaSystemPropertyInFile("user.dir");
        writeCommandLineArgumentsInFile(args);
    }

    private static void writeVmOptionsInFile() throws IOException {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> vmOptions = runtimeMxBean.getInputArguments();
        BufferedWriter vmOptionsWriter = new BufferedWriter(new FileWriter("vm.options.txt"));
        for (String str: vmOptions) {
            vmOptionsWriter.write(str + System.lineSeparator());
        }
        vmOptionsWriter.close();
        // TODO remove sout
        System.out.println("vm options: " + vmOptions);
    }

    private static void writeJavaSystemPropertyInFile(String property) throws IOException {
        String javaProperty = System.getProperty(property);
        BufferedWriter classPathWriter = new BufferedWriter(new FileWriter(property + ".txt"));
        classPathWriter.write(javaProperty);
        classPathWriter.close();
        // TODO remove sout
        System.out.println(property + ": " + javaProperty);
    }

    private static void writeEnvironmentVariableInFile(String envVariable) throws IOException {
        String environmentVariable = System.getenv(envVariable);
        BufferedWriter envVarWriter = new BufferedWriter(new FileWriter("PATH: " + environmentVariable + ".txt"));
        envVarWriter.write(environmentVariable);
        envVarWriter.close();
        // TODO remove sout
        System.out.println(environmentVariable);
    }

    private static void writeCommandLineArgumentsInFile(String[] args) throws IOException {
        BufferedWriter argsWriter = new BufferedWriter(new FileWriter("arguments.txt"));
        for (String argument: args) {
            argsWriter.write(argument + System.lineSeparator());
        }
        argsWriter.close();
        // TODO remove sout
        System.out.println("arguments: " + Arrays.toString(args));
    }
}
