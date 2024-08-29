// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.google.gson.GsonBuilder;
import one.profiler.AsyncProfiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.IntStream;

public class TestMain {
  public static void main(String[] args) throws Exception {
    if (args.length > 0) {
      switch (args[0]) {
        case "dump-launch-parameters" -> dumpLaunchParameters(args);
        case "print-env-var" -> printEnvironmentVariable(args);
        case "print-cwd" -> printCwd();
        case "async-profiler" -> asyncProfiler();
        case "exit-code" -> exitCode(args);
        case "exception" -> exception();
        case "sigsegv" -> segmentationViolation();
        case "main-class" -> mainClassName();
        default -> {
          System.err.println(
            "unexpected command: " + Arrays.toString(args) + '\n' +
            "usage: " + TestMain.class.getName() + " [command [options ...]]\n" +
            "commands:\n" +
            "  dump-launch-parameters [test-args ...] --output /path/to/output/file\n" +
            "  print-env-var [test-args ...] ENV_VAR_NAME\n" +
            "  print-cwd\n" +
            "  async-profiler\n" +
            "  exit-code <number>\n" +
            "  sigsegv\n" +
            "  main-class");
          System.exit(1);
        }
      }
    }
  }

  private static void printEnvironmentVariable(String[] args) {
    String varName = args[args.length - 1];
    System.out.println(varName + "=" + System.getenv(varName));
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
    var properties = new HashMap<>(System.getProperties());
    properties.put("__MAX_HEAP", String.valueOf(Runtime.getRuntime().maxMemory() >> 20));
    properties.put("__GC", ManagementFactory.getGarbageCollectorMXBeans().stream()
      .map(bean -> bean.getName().split(" ", 2)[0])
      .findFirst().orElse("-"));
    var dump = new DumpedLaunchParameters(List.of(args), vmOptions, System.getenv(), properties);

    var gson = new GsonBuilder().setPrettyPrinting().create();
    var jsonText = gson.toJson(dump);
    Files.writeString(outputFile, jsonText);
    System.out.println("Dumped to " + outputFile.getFileName());
  }

  private static void printCwd() {
    System.out.println("CWD=" + Path.of(".").toAbsolutePath());
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static void asyncProfiler() throws IOException {
    var tempFile = Files.createTempFile("async-profiler-", ".lib");
    try {
      var os = System.getProperty("os.name").toLowerCase();
      var arch = System.getProperty("os.arch").toLowerCase();
      var location = os.startsWith("mac") ? "macos" : "aarch64".equals(arch) ? "linux-aarch64" : "linux";
      try (var stream = AsyncProfiler.class.getResourceAsStream("/binaries/" + location + "/libasyncProfiler.so")) {
        Files.copy(Objects.requireNonNull(stream), tempFile, StandardCopyOption.REPLACE_EXISTING);
      }
      var profiler = AsyncProfiler.getInstance(tempFile.toString());
      System.out.println("version=" + profiler.getVersion());
    }
    finally {
      Files.deleteIfExists(tempFile);
    }
  }

  private static void exitCode(String[] args) {
    if (args.length != 2) throw new IllegalArgumentException("Invalid parameters: " + Arrays.toString(args));
    System.exit(Integer.parseInt(args[1]));
  }

  private static void exception() {
    throw new UnsupportedOperationException("aw, snap");
  }

  private static void segmentationViolation() throws NoSuchFieldException, IllegalAccessException {
    var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    var unsafe = (sun.misc.Unsafe) f.get(null);
    unsafe.putAddress(0, 0);
  }

  private static void mainClassName() {
    var stdout = System.out;
    try {
      var buffer = new ByteArrayOutputStream();
      System.setOut(new PrintStream(buffer));
      sun.tools.jps.Jps.main(new String[]{"-l"});
      var pid = String.valueOf(ProcessHandle.current().pid());
      var name = buffer.toString().lines()
        .filter(l -> l.startsWith(pid))
        .map(l -> l.substring(pid.length()).trim())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No " + pid + " in: <<<\n" + buffer.toString().trim() + "\n>>>"));
      stdout.println("main.class=" + name);
    }
    finally {
      System.setOut(stdout);
    }
  }
}
