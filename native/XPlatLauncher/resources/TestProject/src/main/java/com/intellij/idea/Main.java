package com.intellij.idea;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();
        System.out.printf("VMoptions: %s\n", arguments);
        System.out.printf("Class path: %s", System.getProperty("java.class.path"));
        File file = new File("test.txt");
        file.createNewFile();
    }
}
