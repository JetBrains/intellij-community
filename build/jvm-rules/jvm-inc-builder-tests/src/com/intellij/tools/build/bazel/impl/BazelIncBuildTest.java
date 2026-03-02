package com.intellij.tools.build.bazel.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import com.intellij.tools.build.bazel.jvmIncBuilder.NodeSourceSnapshot;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.ConfigurationState;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.Utils;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph.PersistentMVStoreMapletFactory;
import kotlin.metadata.jvm.KmModule;
import kotlin.metadata.jvm.KmPackageParts;
import kotlin.metadata.jvm.KotlinClassMetadata;
import kotlin.metadata.jvm.KotlinModuleMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.DependencyGraph;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl;
import org.jetbrains.jps.dependency.impl.PathSourceMapper;
import org.jetbrains.jps.dependency.java.JVMClassNode;
import org.jetbrains.jps.dependency.java.KotlinMeta;
import org.jetbrains.jps.util.Iterators;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.jetbrains.jps.util.Iterators.collect;
import static org.jetbrains.jps.util.Iterators.filter;
import static org.jetbrains.jps.util.Iterators.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    validateBuildOutput(testDataRelativePath, testOutputDir);

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

      validateBuildOutput(testDataRelativePath, testOutputDir);
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

  private static @NotNull Path getTestOutputDir(String testDataPath) {
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
            int stage = 1;
            for (String sessionLog : readSessionLogs(file)) {
              String targetName = getFileName(file.getParent());
              content.append("\n").append("--------------------------- BEGIN diagnostic log for \"").append(targetName).append(" Stage #").append(stage).append("\" ---------------------------");
              content.append("\n").append(sessionLog);
              content.append("\n").append("--------------------------- END diagnostic log for \"").append(targetName).append(" Stage #").append(stage).append("\" ---------------------------");
              stage += 1;
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

  private static Iterable<String> readSessionLogs(Path diagnostic) throws IOException {
    List<String> logs = new ArrayList<>(); // the first description entry corresponds to the most recent build session
    try (var zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(diagnostic)))) {
      for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
        if (entry.getName().endsWith("/description.txt")) {
          ByteArrayOutputStream buf = new ByteArrayOutputStream();
          zis.transferTo(buf);
          logs.add(buf.toString(StandardCharsets.UTF_8));
        }
      }
    }
    return Iterators.reverse(logs);
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

  protected void validateBuildOutput(String testDataRelativePath, Path testOutputDir) throws IOException {
    // Base validation:
    // 1. For a successful build: for every class node in graph the corresponding .class file must exist in the output and vice versa
    // 2. For a failed build: for every class file in the output, there must exist a class node in the graph.
    //    If a class in the graph corresponds to a non-up-to-date source (deleted or modified), there must be no corresponding class file in the output
    // see StorageManager.cleanBuildState()
    // 3. ConfigurationState with sources with empty digest (=> classes in the graph corresponding to these sources) form dirty scope
    //  4. if sources contain 1 or more kotlin files (= for Kotlin tests), kotlin_module must exist in the output
    //  5. kotlin_module must contain all facade classes from the output without facade classes from dirty scope
    for (BuildOutput output : BuildOutput.scanOutputs(testOutputDir)) {
      try (DependencyGraph graph = output.graph) {
        NodeSourceSnapshot srcSnapshot = output.configState.getSources();
        Set<NodeSource> dirtySources = collect(filter(srcSnapshot.getElements(), src -> srcSnapshot.getDigest(src).isBlank()), new HashSet<>());
        // output .class paths for bytecode
        Set<String> dirtyClasses = new HashSet<>();
        Set<String> allGraphClasses = new HashSet<>();
        Set<String> allOutputClasses = new HashSet<>();

        boolean hasKotlinBytecode = false;
        String kotlinModuleEntryPath = null;
        // class names
        Set<String> kotlinModuleFacadeClassNames = new HashSet<>();
        Set<String> graphFacadeClassNames = new HashSet<>();
        Set<String> graphDirtyFacadeClassNames = new HashSet<>();

        for (NodeSource src : graph.getSources()) {
          boolean isSourceDirty = dirtySources.contains(src);
          Consumer<String> acc = isSourceDirty? p -> {dirtyClasses.add(p); allGraphClasses.add(p);} : allGraphClasses::add;
          for (Node<?, ?> node : graph.getNodes(src)) {
            if (node instanceof JVMClassNode<?,?> clsNode) {
              acc.accept(clsNode.getOutFilePath());
              Iterator<KotlinMeta> metadata = clsNode.getMetadata(KotlinMeta.class).iterator();
              if (metadata.hasNext()) {
                hasKotlinBytecode = true;
                String facadeClassName = null;
                KotlinClassMetadata classMeta = metadata.next().getClassMetadata();
                if (classMeta instanceof KotlinClassMetadata.FileFacade) {
                  facadeClassName = clsNode.getName();
                }
                else if (classMeta instanceof KotlinClassMetadata.MultiFileClassPart multiPart) {
                  facadeClassName = multiPart.getFacadeClassName().replace('.', '/');
                }
                if (facadeClassName != null) {
                  graphFacadeClassNames.add(facadeClassName);
                  if (isSourceDirty) {
                    graphDirtyFacadeClassNames.add(facadeClassName);
                  }
                }
              }
            }
          }
        }

        try (var zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(output.outputJar)))) {
          for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
            String path = entry.getName();
            if (path.endsWith(".class")) {
              allOutputClasses.add(path);
            }
            if (path.startsWith("META-INF/") && path.endsWith(DataPaths.KOTLIN_MODULE_EXTENSION)) {
              kotlinModuleEntryPath = path;
              KmModule module = KotlinModuleMetadata.read(zis.readAllBytes()).getKmModule();
              for (KmPackageParts parts : module.getPackageParts().values()) {
                kotlinModuleFacadeClassNames.addAll(parts.getFileFacades());
                kotlinModuleFacadeClassNames.addAll(parts.getMultiFileClassParts().values());
              }
            }
          }
        }

        if (hasKotlinBytecode) {
          assertNotNull("Test " + testDataRelativePath + " must have '.kotlin_module' file in its output", kotlinModuleEntryPath);
        }

        if (dirtySources.isEmpty()) {
          // => successful build
          assertTrue(dirtyClasses.isEmpty());
          assertTrue(graphDirtyFacadeClassNames.isEmpty());
          assertEquals(allGraphClasses, allOutputClasses);
          if (hasKotlinBytecode) {
            assertEquals("Set of registered Kotlin facade classes in the dependency graph must be the same as in '.kotlin_module' output file", graphFacadeClassNames, kotlinModuleFacadeClassNames);
          }
        }
        else {
          // => build completed with errors
          assertTrue("All classes in the output should be registered in the dependency graph", allGraphClasses.containsAll(allOutputClasses));
          for (String graphClass : allGraphClasses) {
            if (dirtyClasses.contains(graphClass)) {
              assertFalse("Classes from modified or deleted sources must not be in the output: " + graphClass, allOutputClasses.contains(graphClass));
            }
            else {
              assertTrue("Classes from up-to-date sources must be in the output: " + graphClass, allOutputClasses.contains(graphClass));
            }
          }

          if (hasKotlinBytecode) {
            assertTrue("All Kotlin facade classes from the '.kotlin_module' file must be registered in the dependency graph", graphFacadeClassNames.containsAll(kotlinModuleFacadeClassNames));
            for (String graphClassName : graphFacadeClassNames) {
              if (graphDirtyFacadeClassNames.contains(graphClassName)) {
                assertFalse("Facade classes from modified or deleted sources must not be in '.kotlin_module' file: " + graphClassName, kotlinModuleFacadeClassNames.contains(graphClassName));
              }
              else {
                assertTrue("Facade classes from up-to-date sources must be in '.kotlin_module' file: " + graphClassName, kotlinModuleFacadeClassNames.contains(graphClassName));
              }
            }
          }
        }

      }
    }
  }

  private record BuildOutput(DependencyGraph graph, ConfigurationState configState, Path outputJar) {

    static Iterable<BuildOutput> scanOutputs(Path testOutputDir) throws IOException {
      List<Path> targetOutputs = Files.list(testOutputDir).filter(path -> matches(path, ".jar") && !matches(path, DataPaths.ABI_JAR_SUFFIX)).toList();

      return map(targetOutputs, output -> {
        try {
          String dataDirName = DataPaths.truncateExtension(getFileName(output)) + DataPaths.DATA_DIR_NAME_SUFFIX;
          Path graphPath = output.resolveSibling(dataDirName).resolve(DataPaths.DEP_GRAPH_FILE_NAME);
          Path configStatePath = graphPath.resolveSibling(DataPaths.CONFIG_STATE_FILE_NAME);

          assertTrue("Dependency graph storage is missing for the output " + output, Files.exists(graphPath));
          assertTrue("Configuration state storage is missing for the output " + output, Files.exists(configStatePath));

          return new BuildOutput(
            new DependencyGraphImpl(new PersistentMVStoreMapletFactory(graphPath.toString(), 1)),
            new ConfigurationState(new PathSourceMapper(), configStatePath),
            output
          );
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }

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
