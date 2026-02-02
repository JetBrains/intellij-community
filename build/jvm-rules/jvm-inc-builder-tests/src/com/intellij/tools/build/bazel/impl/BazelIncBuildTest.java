package com.intellij.tools.build.bazel.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ComparisonFailure;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 Base class describing the main test scenario for incremental build tests
 Expected test data layout:

 Root test data dir
 |
 --testDir_1
 --testDir_2
 |
 --...
 |
 --testDir_N
   |
   --module_1
   --module_2
   |
   --...
   |
   --module_N
      |
      --File.java
      --File.kt
      --File.java.new
      --File.java.delete
      --File.kt.new1
      --build.log
 */
public abstract class BazelIncBuildTest {

  private static Path ourTestDataRoot;
  private static Path ourTestDataWorkRoot;
  private static String ourBazelRunnerPath;
  private static Path ourOutputBinRoot;

  public static final String BAZEL_EXECUTABLE = "jvm-inc-builder.bazel.executable";
  public static final String BAZEL_TEST_WORKSPACE_FILE = "jvm-inc-builder.module.bazel.txt";
  public static final String RULES_JVM_SNAPSHOT_FILE = "jvm-inc-builder.rules.jvm.zip";

  /*
    If disabled, the test project output directory will not be deleted. Useful for debugging
  */
  private static final boolean OUTPUT_FULL_CLEAN = Boolean.parseBoolean(System.getProperty("jvm-inc-builder.test.cleanup", "true"));
  private static final Predicate<String> ACTION_EXTENSION_MATCHER = Pattern.compile("\\.(?:log|delete|new)\\d*").asMatchPredicate();

  @BeforeClass
  public static void setupWorkDir() throws Exception {
    ourTestDataWorkRoot = Files.createTempDirectory("bazel-rules-jvm-tests");
    String bazelExePath = System.getProperty(BAZEL_EXECUTABLE);
    String moduleBazelTxtPath = System.getProperty(BAZEL_TEST_WORKSPACE_FILE);
    String rulesJvmPath = System.getProperty(RULES_JVM_SNAPSHOT_FILE);

    assertNotNull(BAZEL_EXECUTABLE + " system property is not set", bazelExePath);
    assertNotNull(BAZEL_TEST_WORKSPACE_FILE + " system property is not set", moduleBazelTxtPath);
    assertNotNull(RULES_JVM_SNAPSHOT_FILE + " system property is not set", rulesJvmPath);

    ourBazelRunnerPath = bazelExePath;
    assertNotNull("Path to bazel executable is expected to be set in \"" + BAZEL_EXECUTABLE + "\" system property", ourBazelRunnerPath);
    assertTrue("Specified path to bazel executable does not exist: \"" + ourBazelRunnerPath + "\"", Files.exists(Path.of(ourBazelRunnerPath)));

    ourTestDataRoot = Paths.get(moduleBazelTxtPath).getParent();
    assertTrue("Test data root \"" + ourTestDataRoot + "\" does not exist", Files.isDirectory(ourTestDataRoot));

    String rulesJvmZipPath = rulesJvmPath;
    assertTrue("Rules JVM zip file does not exist: " + rulesJvmZipPath, Files.exists(Path.of(rulesJvmZipPath)));

    Utils.deleteRecursively(ourTestDataWorkRoot);
    Files.createDirectories(ourTestDataWorkRoot);
    for (File file : ourTestDataRoot.toFile().listFiles(File::isFile)) {
      copyTestDataFile(file.toPath(), ourTestDataWorkRoot);
    }

    String moduleBazelContent = Files.readString(ourTestDataWorkRoot.resolve("MODULE.bazel"));

    String marker = "ABSOLUTE_RULES_JVM_ARTIFACT_PATH";

    if (!moduleBazelContent.contains(marker)) {
      throw new IllegalStateException("Expected to find ABSOLUTE_RULES_JVM_ARTIFACT_PATH in MODULE.bazel");
    }
    if (!rulesJvmZipPath.startsWith("/")) {
      rulesJvmZipPath = "/" + rulesJvmZipPath;
    }
    moduleBazelContent = moduleBazelContent.replace(marker, rulesJvmZipPath);
    Files.writeString(ourTestDataWorkRoot.resolve("MODULE.bazel"), moduleBazelContent);

    // expected to be the module root output path, like '.../execroot/_main/bazel-out'
    ExecutionResult execResult = runBazelCommand(
      OutputConsumer.lastLineConsumer(),
      Duration.ofMinutes(5),
      "info",
      "output_path"
    );
    execResult.assertSuccessful();
    String infoCmdOutput = execResult.getOutput();
    // assuming 'jvm-fastbuild' fixed name if --experimental_platform_in_output_dir flag is used in .bazelrc
    ourOutputBinRoot = Path.of(infoCmdOutput).resolve("jvm-fastbuild").resolve("bin");
  }

  @AfterClass
  public static void cleanupStatic() throws Exception {
    try {
      if (OUTPUT_FULL_CLEAN) {
        runBazelCommand(
          OutputConsumer.allLinesConsumer(),
          Duration.ofMinutes(10),
          "clean",
          "--expunge"
        ).assertSuccessful();
      }
      runBazelCommand(
        OutputConsumer.allLinesConsumer(),
        Duration.ofMinutes(1),
        "shutdown"
      ).assertSuccessful();
    }
    finally {
      Utils.deleteRecursively(ourTestDataWorkRoot);
    }
  }

  protected ExecutionResult performTest(String testDataRelativePath) throws Exception {
    return performTest(1, testDataRelativePath); // most common case: initial build, change, incremental build after the change
  }

  protected ExecutionResult performTest(int makesCount, String testDataRelativePath) throws Exception {
    Path testDataDir = ourTestDataRoot.resolve(testDataRelativePath);      // the initial test data files
    Path testWorkDir = ourTestDataWorkRoot.resolve(testDataRelativePath);  // the working root directory for sources of this particular test
    copyRecursively(testDataDir, testWorkDir.getParent(), path -> !ACTION_EXTENSION_MATCHER.test(getExtension(path)));

    Path testOutputDir = getTestOutputDir(testDataRelativePath);
    Path buildLogFile = testOutputDir.resolve(DataPaths.BUILD_LOG_FILE_NAME);
    Path expectedBuildLogFile = testDataDir.resolve(DataPaths.BUILD_LOG_FILE_NAME);
    assertTrue("File with expected build log " + expectedBuildLogFile + " must exist.", Files.exists(expectedBuildLogFile));

    Utils.deleteRecursively(testOutputDir); // cleanup from previous run

    String bazelTarget = "//" + testDataRelativePath + "/...";

    runBazelBuild(bazelTarget).assertSuccessful(); // the initial build
    assertTrue("Tests output root directory " + testOutputDir + " should exist. Probably test expectations differ from Bazel's current output dir naming policy", Files.exists(testOutputDir));

    ExecutionResult result = null;
    StringBuilder buildLog = new StringBuilder();
    for (int idx = 0; idx < makesCount; idx++) {
      modify(testDataDir, testWorkDir, idx);
      Utils.deleteIfExists(buildLogFile);

      result = runBazelBuild(bazelTarget);

      if (Files.exists(buildLogFile)) {
        buildLog.append("\n").append("================ Step #").append(idx + 1).append(" =================");
        buildLog.append("\n").append(Files.readString(buildLogFile, StandardCharsets.UTF_8).trim());
        buildLog.append("\n").append("------------------------------------------");
        buildLog.append("\n").append("Exit code: ").append(result.isSuccessful()? "OK" : "ERROR");
      }
    }

    String expectedBuildLog = Files.readString(expectedBuildLogFile, StandardCharsets.UTF_8).replaceAll("\r\n?", "\n").trim();
    String actualBuildLog = buildLog.toString().trim();
    if (!expectedBuildLog.equals(actualBuildLog)) {
      // only collect diagnostics on failures
      throw new ComparisonFailure(collectDiagnostics(testOutputDir), expectedBuildLog, actualBuildLog);
    }
    if (result != null && result.isSuccessful()) {
      // todo: rebuild from scratch and compare graphs
    }

    return result;
  }

  private @NotNull Path getTestOutputDir(String testDataPath) {
    return ourOutputBinRoot.resolve(testDataPath);
  }

  private static String collectDiagnostics(Path testOutputDir) {
    StringBuilder content = new StringBuilder();
    content.append("Test output directory: ").append(testOutputDir).append("\n");
    try {
      Files.walkFileTree(testOutputDir, new SimpleFileVisitor<>() {
        @Override
        public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
          if (matches(file, DataPaths.DIAGNOSTIC_FILE_NAME_SUFFIX)) {
            String sessionLog = readLatestEntry(file);
            if (sessionLog != null) {
              String targetName = getFileName(file.getParent());
              content.append("\n").append("--------------------------- BEGIN diagnostic log for \"").append(targetName).append("\" ---------------------------");
              content.append("\n").append(sessionLog);
              content.append("\n").append("--------------------------- END diagnostic log for \"").append(targetName).append("\" ---------------------------");
            }
          }
          return FileVisitResult.CONTINUE;
        }
      });
    }
    catch (IOException e) {
      StringWriter buf = new StringWriter();
      e.printStackTrace(new PrintWriter(buf));
      content.append(buf);
    }
    return content.toString();
  }

  private static String readLatestEntry(Path diagnostic) throws IOException {
    try (var zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(diagnostic)))) {
      for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
        if (entry.getName().endsWith("/description.txt")) {
          // the first description entry corresponds to the most recent build session
          ByteArrayOutputStream buf = new ByteArrayOutputStream();
          zis.transferTo(buf);
          return buf.toString(StandardCharsets.UTF_8);
        }
      }
    }
    return null;
  }

  protected void modify(Path testDataDir, Path testWorkDir, int stage) throws IOException {
    Files.walkFileTree(testDataDir, new SimpleFileVisitor<>() {
      final String toRemoveSuffix = stage > 0? ".delete" + stage : ".delete";
      final String toUpdateSuffix = stage > 0? ".new" + stage : ".new";

      @Override
      public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
        if (matches(file, toUpdateSuffix)) {
          copyFile(file, getTargetPath(file));
        }
        else if (matches(file, toRemoveSuffix)) {
          Files.delete(getTargetPath(file));
        }
        return FileVisitResult.CONTINUE;
      }

      private Path getTargetPath(Path srcPath) {
        Path destPath = testWorkDir.resolve(testDataDir.relativize(srcPath));
        String fileName = getFileName(srcPath);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0? destPath.resolveSibling(fileName.substring(0, dotIndex)) : destPath;
      }

    });
  }

  @NotNull
  protected ExecutionResult runBazelBuild(String... options) throws Exception {
    return runBazelCommand(
      OutputConsumer.allLinesConsumer(),
      Duration.ofMinutes(10),
      "build",
      options
    );
  }

  @NotNull
  protected static ExecutionResult runBazelCommand(
    OutputConsumer outputSink,
    Duration timeout,
    String command,
    String... options
  ) throws Exception {
    ProcessBuilder processBuilder = new ProcessBuilder(
      ourBazelRunnerPath, "--nosystem_rc", "--nohome_rc", command
    );
    processBuilder.command().addAll(List.of(options));
    processBuilder.command().addAll(List.of("--color=no", "--curses=no"));
    String commandText = String.join(" ", processBuilder.command());

    processBuilder.redirectErrorStream(true);
    processBuilder.directory(ourTestDataWorkRoot.toFile());

    Process proc = processBuilder.start();
    long startNanos = System.nanoTime();
    try {
      OutputConsumer allOutput = OutputConsumer.allLinesConsumer();
      Thread readerThread = outputReader(outputSink, proc, allOutput);

      boolean exitedInTime = proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!exitedInTime) {
        proc.destroy();
        if (!proc.waitFor(5, TimeUnit.SECONDS)) {
          proc.destroyForcibly();
          proc.waitFor(5, TimeUnit.SECONDS);
        }
      }

      readerThread.join(TimeUnit.SECONDS.toMillis(5));

      if (!exitedInTime) {
        return ExecutionResult.create(
          -1,
          "Bazel command timed out after " + timeout.toSeconds() + " sec\n" + allOutput.getResult()
        );
      }

      int exitCode = proc.exitValue();
      return ExecutionResult.create(exitCode, exitCode == 0? outputSink.getResult() : allOutput.getResult());
    }
    finally {
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      System.out.println("runBazelCommand[" + elapsedMillis +  " ms]: " + commandText);
    }
  }

  private static @NotNull Thread outputReader(OutputConsumer outputSink, Process proc, OutputConsumer allOutput) {
    Thread readerThread = new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          outputSink.consumeLine(line);
          allOutput.consumeLine(line);
        }
      }
      catch (IOException ignored) {
      }
    }, "bazel-inc-build-test-output");
    readerThread.setDaemon(true);
    readerThread.start();
    return readerThread;
  }

  private static void copyRecursively(Path source, Path toDir, Predicate<Path> filter) throws IOException {
    if (!Files.isDirectory(source)) {
      copyTestDataFile(source, toDir);
    }
    else {
      Files.walkFileTree(source, new SimpleFileVisitor<>() {
        private Path targetDir = toDir;
        @Override
        public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) {
          targetDir = targetDir.resolve(dir.getFileName());
          return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
          if (filter.test(file)) {
            copyTestDataFile(file, targetDir);
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, @Nullable IOException exc) {
          targetDir = targetDir.getParent();
          return FileVisitResult.CONTINUE;
        }
      });
    }
  }

  private static void copyTestDataFile(@NotNull Path file, @NotNull Path targetDir) throws IOException {
    String fName = getFileName(file);
    if (fName.endsWith(".bazel.txt")) {
      fName = fName.substring(0, fName.length() - ".txt".length());
    }
    copyFile(file, targetDir.resolve(fName));
  }

  private static void copyFile(@NotNull Path sourceFile, Path targetFile) throws IOException {
    try {
      Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }
    catch (NoSuchFileException e) {
      Files.createDirectories(targetFile.getParent());
      Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static @NotNull String getExtension(Path path) {
    String fileName = getFileName(path);
    int dotIndex = fileName.lastIndexOf('.');
    return dotIndex >= 0? fileName.substring(dotIndex) : "";
  }

  private static boolean matches(Path path, String suffix) {
    return getFileName(path).endsWith(suffix);
  }

  private static @NotNull String getFileName(Path p) {
    return p.getFileName().toString();
  }
}
