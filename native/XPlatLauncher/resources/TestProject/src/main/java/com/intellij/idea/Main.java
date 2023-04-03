// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class Main {
  @SuppressWarnings("RedundantLabeledSwitchRuleCodeBlock")
  public static void main(String[] args) throws Exception {
    if (args.length > 0) {
      switch (args[0]) {
        case "dump-launch-parameters" -> {
          dumpLaunchParameters(args);
        }
        case "sigsegv" -> {
          segmentationViolation();
        }
        default -> {
          System.err.println(
            "unexpected command: " + Arrays.toString(args) + '\n' +
            "usage: " + Main.class.getName() + " [command [options ...]]\n" +
            "commands:\n" +
            "  dump-launch-parameters  [test-args ...] --output /path/to/output/file\n" +
            "  sigsegv");
          System.exit(1);
        }
      }
    }
  }

  private static void dumpLaunchParameters(String[] args) throws IOException {
    var optionIdx = IntStream.range(0, args.length).filter(i -> "--output".equals(args[i])).findFirst().orElse(-1);
    if (optionIdx < 0 || optionIdx > args.length - 2) {
      throw new IllegalArgumentException("Invalid parameters: " + Arrays.toString(args));
    }
    var outputFile = Path.of(args[optionIdx + 1]);
    Files.createDirectories(outputFile.getParent());

    record DumpedLaunchParameters(
      List<String> cmdArguments,
      List<String> vmOptions,
      Map<String, String> environmentVariables,
      Map<?, ?> systemProperties
    ) { }

    var vmOptions = ManagementFactory.getRuntimeMXBean().getInputArguments();
    var dump = new DumpedLaunchParameters(List.of(args), vmOptions, System.getenv(), System.getProperties());

    var gson = new GsonBuilder().setPrettyPrinting().create();
    var jsonText = gson.toJson(dump);
    System.out.println(jsonText);

    Files.writeString(outputFile, jsonText);
    System.out.println("Dumped to " + outputFile.toAbsolutePath());
  }

  private static void segmentationViolation() throws NoSuchFieldException, IllegalAccessException {
    var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    var unsafe = (sun.misc.Unsafe) f.get(null);
    unsafe.putAddress(0, 0);
  }
}
