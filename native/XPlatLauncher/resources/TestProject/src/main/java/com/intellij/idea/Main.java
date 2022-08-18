package com.intellij.idea;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class Main {
    private static Path baseOutputDirectory;

    public static void main(String[] args) throws IOException {
        String executable = args[0];

        // on Windows current directory gets changed to jvm bin so that jvm.dll can load
        baseOutputDirectory = Path.of(executable).getParent();

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
        File output = baseOutputDirectory.resolve("vm.options.txt").toFile();
        BufferedWriter vmOptionsWriter = new BufferedWriter(new FileWriter(output));
        for (String str: vmOptions) {
            vmOptionsWriter.write(str + System.lineSeparator());
        }
        vmOptionsWriter.close();
        // TODO remove sout
        System.out.println("vm options: " + vmOptions);
    }

    private static void writeJavaSystemPropertyInFile(String property) throws IOException {
        String javaProperty = System.getProperty(property);
        File output = baseOutputDirectory.resolve(property + ".txt").toFile();
        BufferedWriter classPathWriter = new BufferedWriter(new FileWriter(output));
        classPathWriter.write(javaProperty);
        classPathWriter.close();
        // TODO remove stdout
        System.out.println(property + ": " + javaProperty);
    }

    private static void writeEnvironmentVariableInFile(String envVariable) throws IOException {
        String environmentVariable = System.getenv(envVariable);
        File output = baseOutputDirectory.resolve("PATH: " + environmentVariable + ".txt").toFile();
        BufferedWriter envVarWriter = new BufferedWriter(new FileWriter(output));
        envVarWriter.write(environmentVariable);
        envVarWriter.close();
        // TODO remove sout
        System.out.println(environmentVariable);
    }

    private static void writeCommandLineArgumentsInFile(String[] args) throws IOException {
        File output = baseOutputDirectory.resolve("arguments.txt").toFile();
        BufferedWriter argsWriter = new BufferedWriter(new FileWriter(output));
        for (String argument: args) {
            argsWriter.write(argument + System.lineSeparator());
        }
        argsWriter.close();
        // TODO remove sout
        System.out.println("arguments: " + Arrays.toString(args));
    }
}
