// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.Utils;
import org.junit.ComparisonFailure;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the rebuild behavior after the build process gets killed at a defined point (MRI-5372).
 * A crash point models the kill: the worker halts via Runtime.halt(), which skips the finally blocks
 * and the shutdown hooks, like an external process kill does.
 * The test scenario: Api.greet(String) changes to Api.greet(Object). The change is source compatible
 * and binary incompatible, so a stale caller class in the output would fail at run time.
 * Every crash point has its own test data directory with its own expected build.log.
 */
public class CrashRecoveryTest extends BazelIncBuildTest {
  private static final String OLD_GREET_DESCRIPTOR = "(Ljava/lang/String;)Ljava/lang/String;";
  private static final String NEW_GREET_DESCRIPTOR = "(Ljava/lang/Object;)Ljava/lang/String;";

  @Test
  public void testCrashAfterRoundIntegrate() throws Exception {
    // the MRI-5372 window: the graph is committed ahead of the config state and the jar backup
    performCrashRecoveryTest("worker/crashRecovery/afterRoundIntegrate", "after_round_integrate", true);
  }

  @Test
  public void testCrashBeforeSaveState() throws Exception {
    // the storages are closed, the backup and the config state are not written yet
    performCrashRecoveryTest("worker/crashRecovery/beforeSaveState", "before_save_state", true);
  }

  @Test
  public void testCrashAfterDepsBackup() throws Exception {
    // the backup holds the new jars, the config state is not written yet
    performCrashRecoveryTest("worker/crashRecovery/afterDepsBackup", "after_deps_backup", true);
  }

  @Test
  public void testCrashBeforeBuildStart() throws Exception {
    // control: nothing durable is changed yet, so the recovery build must stay incremental
    performCrashRecoveryTest("worker/crashRecovery/beforeBuildStart", "before_build_start", false);
  }

  /**
   * Tests the rebuild behavior after the build process is killed at the given crash point.
   * The flow: build the project, modify the sources, run the build with the active crash point.
   * The worker halts at the point and the build fails. Then run the recovery build without the crash point.
   * The recovery build must succeed and its build log must match the expected log.
   * @param expectStateDeleted whether the config state file must be absent after the crashed build
   */
  private void performCrashRecoveryTest(String testDataRelativePath, String crashPoint, boolean expectStateDeleted) throws Exception {
    Path testDataDir = ourTestDataRoot.resolve(testDataRelativePath);
    Path testWorkDir = ourTestDataWorkRoot.resolve(testDataRelativePath);
    copyRecursively(testDataDir, testWorkDir.getParent(), path -> !ACTION_EXTENSION_MATCHER.test(getExtension(path)));

    Path testOutputDir = getTestOutputDir(testDataRelativePath);
    Path buildLogFile = testOutputDir.resolve(DataPaths.BUILD_LOG_FILE_NAME);
    Path expectedBuildLogFile = testDataDir.resolve(DataPaths.BUILD_LOG_FILE_NAME);
    assertTrue("File with expected build log " + expectedBuildLogFile + " must exist.", Files.exists(expectedBuildLogFile));

    Utils.deleteRecursively(testOutputDir); // cleanup from previous run

    String bazelTarget = "//" + testDataRelativePath + "/...";

    runBazelBuild(bazelTarget).assertSuccessful(); // the initial build
    validateBuildOutput(testDataRelativePath, testOutputDir);

    modify(testDataDir, testWorkDir, 0);

    // the worker halts at the crash point, so the build must fail
    var crashResult = runBazelBuild(bazelTarget, "--@rules_jvm//:jvm-builder-jvm_flags=//:jvm-inc-builder-test_jvm_flags-crash_" + crashPoint);
    assertFalse("The build with the active crash point '" + crashPoint + "' must fail:\n" + crashResult.getOutput(), crashResult.isSuccessful());

    List<Path> dataDirs;
    try (var children = Files.list(testOutputDir)) {
      dataDirs = children.filter(p -> Files.isDirectory(p) && getFileName(p).endsWith(DataPaths.DATA_DIR_NAME_SUFFIX)).toList();
    }
    assertFalse("A builder data directory must exist after the crashed build", dataDirs.isEmpty());
    for (Path dataDir : dataDirs) {
      Path configFile = dataDir.resolve(DataPaths.CONFIG_STATE_FILE_NAME);
      Path graphFile = dataDir.resolve(DataPaths.DEP_GRAPH_FILE_NAME);
      if (expectStateDeleted) {
        assertFalse("The config state must be absent after the crash: " + configFile, Files.exists(configFile));
        assertTrue("The dependency graph must survive the crash: " + graphFile, Files.exists(graphFile));
      }
      else {
        assertTrue("The config state must be present after the crash: " + configFile, Files.exists(configFile));
      }
    }

    // the recovery build runs without the crash point and must repair the build state
    Utils.deleteIfExists(buildLogFile);
    runBazelBuild(bazelTarget).assertSuccessful();
    validateBuildOutput(testDataRelativePath, testOutputDir);

    String expectedBuildLog = Files.readString(expectedBuildLogFile, StandardCharsets.UTF_8).replaceAll("\r\n?", "\n").trim();
    String actualBuildLog = Files.exists(buildLogFile)? Files.readString(buildLogFile, StandardCharsets.UTF_8).replaceAll("\r\n?", "\n").trim() : "";
    if (!expectedBuildLog.equals(actualBuildLog)) {
      throw new ComparisonFailure(collectDiagnostics(testOutputDir), expectedBuildLog, actualBuildLog);
    }
  }

  @Override
  protected void validateOutputArtifacts(BuildOutput output) throws IOException {
    try (var zip = new ZipFile(output.outputJar().toFile())) {
      boolean apiUsesNewApi = readClass(zip, "repro/Api.class").contains(NEW_GREET_DESCRIPTOR);
      String expected = apiUsesNewApi? NEW_GREET_DESCRIPTOR : OLD_GREET_DESCRIPTOR;
      String stale = apiUsesNewApi? OLD_GREET_DESCRIPTOR : NEW_GREET_DESCRIPTOR;
      for (String caller : List.of("repro/Caller1.class", "repro/Caller2.class")) {
        String bytecode = readClass(zip, caller);
        assertTrue("Caller " + caller + " in " + zip.getName() + " must call the API with signature " + expected, bytecode.contains(expected));
        assertFalse("Caller " + caller + " in " + zip.getName() + " must not keep the stale API signature " + stale, bytecode.contains(stale));
      }
    }
  }

  private static String readClass(ZipFile zip, String entryName) throws IOException {
    ZipEntry entry = zip.getEntry(entryName);
    assertNotNull("Entry " + entryName + " must exist in " + zip.getName(), entry);
    try (var in = zip.getInputStream(entry)) {
      // a class file holds descriptors as modified UTF-8; the greet descriptors are pure ASCII, so reading class bytes as ASCII string is enough
      return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
    }
  }
}
