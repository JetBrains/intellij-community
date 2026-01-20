package com.intellij.tools.build.bazel.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 Base class describing the main test scenario for incremental build tests
 Expected test data layout:

 root test data dir
 |
 --testDir_1
 --testDir_2
 |
 --....
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BazelIncBuildTest {

  public static final String WORKSPACE_ROOT_PROPERTY = "jvm-inc-builder.workspace.root";
  public static final String BAZEL_EXECUTABLE = "jvm-inc-builder.bazel.executable";

  /*
    If disabled, the test project output directory will not be deleted. Useful for debugging
  */
  private static final boolean OUTPUT_FULL_CLEAN = Boolean.parseBoolean(System.getProperty("jvm-inc-builder.test.cleanup", "true"));
  private static final String WORK_DIR_NAME = "bazel-rules-jvm-tests-c3a22ca0-16c8-4d20-9174-d6d2073faf35";
  private static final Predicate<String> ACTION_EXTENSION_MATCHER = Pattern.compile("\\.(?:log|delete|new)\\d*").asMatchPredicate();

  private final Path myTestDataWorkRoot;
  private final String myBazelRunnerPath;

  private Path myTestDataRoot;
  private String myModuleOverrideFlag;
  private Path myOutputBinRoot;

  public BazelIncBuildTest() {
    Path tmpRoot = Path.of(System.getProperty("java.io.tmpdir"));
    myTestDataWorkRoot = tmpRoot.resolve(WORK_DIR_NAME).normalize();  // the work root should be the same across different test sessions
    String bazelPath = System.getProperty("jvm-inc-builder.bazel.executable");
    myBazelRunnerPath = bazelPath != null? bazelPath.replace(File.separatorChar, '/') : null;
  }

  @BeforeAll
  protected void setupWorkDir() throws Exception {
    assertNotNull(myBazelRunnerPath, "Path to bazel executable is expected to be set in \"" + BAZEL_EXECUTABLE + "\" system property");
    assertTrue(Files.exists(Path.of(myBazelRunnerPath)), () -> "Specified path to bazel executable does not exist: \"" + myBazelRunnerPath + "\"");

    String wsRootPath = System.getProperty(WORKSPACE_ROOT_PROPERTY);
    assertTrue(wsRootPath != null && !wsRootPath.isBlank(), "Workspace root path for 'rules_jvm' is expected to be set in \"" + WORKSPACE_ROOT_PROPERTY + "\" system property");
    Path wsRoot = Path.of(wsRootPath).toRealPath();
    assertTrue(Files.exists(wsRoot), () -> "Passed workspace root for 'rules_jvm' \"" + wsRoot + "\" does not exist");

    myTestDataRoot = wsRoot.resolve("jvm-inc-builder-tests").resolve("testData");
    assertTrue(Files.exists(myTestDataRoot), () -> "Test data root \"" + myTestDataRoot + "\" does not exist");

    myModuleOverrideFlag = "--override_module=rules_jvm=" + wsRoot.toString().replace(File.separatorChar, '/');

    if (Files.exists(myTestDataWorkRoot)) {
      for (Path path : Files.list(myTestDataWorkRoot).toList()) {
        Utils.deleteRecursively(path); // local cleanup
      }
    }
    else {
      Files.createDirectories(myTestDataWorkRoot);
    }
    for (Path path : Files.list(myTestDataRoot).filter(Files::isRegularFile).toList()) {
      copyTestDataFile(path, myTestDataWorkRoot);
    }

    // expected to be the module root output path, like '.../execroot/_main/bazel-out'
    ExecutionResult execResult = runBazelCommand(OutputConsumer.lastLineConsumer(), "info", "output_path");
    execResult.assertSuccessful();
    String infoCmdOutput = execResult.getOutput();
    // assuming 'jvm-fastbuild' fixed name, if --experimental_platform_in_output_dir flag is used in .bazelrc
    myOutputBinRoot = Path.of(infoCmdOutput).resolve("jvm-fastbuild").resolve("bin");

    //System.out.println("Expected output root for test cases: " + myBaseOutputRoot);
  }

  @AfterAll
  protected void cleanup() throws Exception {
    try {
      if (OUTPUT_FULL_CLEAN) {
        runBazelCommand(OutputConsumer.allLinesConsumer(), "clean", "--expunge").assertSuccessful();
      }
      runBazelCommand(OutputConsumer.allLinesConsumer(), "shutdown").assertSuccessful();
    }
    finally {
      Utils.deleteRecursively(myTestDataWorkRoot);
    }
  }

  protected ExecutionResult performTest(String testDataRelativePath) throws Exception {
    return performTest(1, testDataRelativePath); // most common case: initial build, change, incremental build after the change
  }

  protected ExecutionResult performTest(int makesCount, String testDataRelativePath) throws Exception {
    Path testDataDir = myTestDataRoot.resolve(testDataRelativePath);      // the initial test data files
    Path testWorkDir = myTestDataWorkRoot.resolve(testDataRelativePath);  // the working root directory for sources of this particular test
    copyRecursively(testDataDir, testWorkDir.getParent(), path -> !ACTION_EXTENSION_MATCHER.test(getExtension(path)));

    Path testOutputDir = getTestOutputDir(testDataRelativePath);
    Path buildLogFile = testOutputDir.resolve(DataPaths.BUILD_LOG_FILE_NAME);
    Path expectedBuildLogFile = testDataDir.resolve(DataPaths.BUILD_LOG_FILE_NAME);
    assertTrue(Files.exists(expectedBuildLogFile), () -> "File with expected build log " + expectedBuildLogFile + " must exist.");

      Utils.deleteRecursively(testOutputDir); // cleanup from previous run

    String bazelTarget = "//" + testDataRelativePath + "/...";

    runBazelBuild(bazelTarget).assertSuccessful(); // the initial build
    assertTrue(Files.exists(testOutputDir), () -> "Tests output root directory " + testOutputDir + " should exist. Probably test expectations differ from Bazel's current output dir naming policy");

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
    assertEquals(expectedBuildLog, actualBuildLog, () -> collectDiagnostics(testOutputDir));

    if (result != null && result.isSuccessful()) {
      // todo: rebuild from scratch and compare graphs
    }

    return result;
  }

  private @NotNull Path getTestOutputDir(String testDataPath) {
    return myOutputBinRoot.resolve(testDataPath);
  }

  private static String collectDiagnostics(Path testOutputDir) {
    StringBuilder content = new StringBuilder();
    Comparator<Path> byFileName = Comparator.comparing(p -> getFileName(p));
    try {
      content.append("Test output directory: ").append(testOutputDir).append("\n");
      for (Path dataDir : Files.list(testOutputDir).filter(p -> matches(p, DataPaths.DATA_DIR_NAME_SUFFIX) && Files.isDirectory(p)).sorted(byFileName).toList()) {
        Path diagnostic = Files.list(dataDir).filter(p -> matches(p, DataPaths.DIAGNOSTIC_FILE_NAME_SUFFIX) && Files.isRegularFile(p)).findFirst().orElse(null);
        String sessionLog = diagnostic != null? readLatestEntry(diagnostic) : null;
        if (sessionLog != null) {
          String targetName = getFileName(dataDir);
          content.append("\n").append("--------------------------- BEGIN diagnostic log for \"").append(targetName).append("\" ---------------------------");
          content.append("\n").append(sessionLog);
          content.append("\n").append("--------------------------- END diagnostic log for \"").append(targetName).append("\" ---------------------------");
        }
      }
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
          return new String(buf.toByteArray(), StandardCharsets.UTF_8);
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
          Files.copy(file, getTargetPath(file), StandardCopyOption.REPLACE_EXISTING);
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
    return runBazelCommand(OutputConsumer.allLinesConsumer(), "build", options);
  }

  @NotNull
  protected ExecutionResult runBazelCommand(OutputConsumer outputSink, String command, String... options) throws Exception {
    ProcessBuilder processBuilder = new ProcessBuilder(
      myBazelRunnerPath, "--nosystem_rc", "--nohome_rc", "--max_idle_secs=10", command
    );
    processBuilder.command().addAll(List.of(options));
    processBuilder.command().addAll(List.of(
      "--color=no", "--curses=no", myModuleOverrideFlag
    ));

    processBuilder.redirectErrorStream(true);
    processBuilder.directory(myTestDataWorkRoot.toFile());

    Process proc = processBuilder.start();
    OutputConsumer allOutput = OutputConsumer.allLinesConsumer();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        outputSink.consumeLine(line);
        allOutput.consumeLine(line);
      }
    }
    
    int exitCode = proc.waitFor();
    return ExecutionResult.create(exitCode, exitCode == 0? outputSink.getResult() : allOutput.getResult());
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
    Path targetFile = targetDir.resolve(fName);
    try {
      Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }
    catch (NoSuchFileException e) {
      Files.createDirectories(targetDir);
      Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
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
