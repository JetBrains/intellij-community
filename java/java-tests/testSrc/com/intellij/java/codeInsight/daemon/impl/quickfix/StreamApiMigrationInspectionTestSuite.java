// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixOptions;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

public class StreamApiMigrationInspectionTestSuite {
  private static @NotNull String testDataPath(@NotNull String folder) {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/streamApiMigration/" + folder;
  }

  private static TestFixture<LightQuickFixFixture> createQuickFixFixture(TestFixture<LightJavaProject> projectFixture,
                                                                       String testDataPath) {
    return lightQuickFixFixture(projectFixture, testDataPath, () -> List.of(createInspection()),
                                LightQuickFixOptions.DEFAULT.withLanguageLevelFromFileName());
  }

  private static StreamApiMigrationInspection createInspection() {
    StreamApiMigrationInspection inspection = new StreamApiMigrationInspection();
    inspection.SUGGEST_FOREACH = true;
    return inspection;
  }

  private static void doTest(LightQuickFixFixture quickFix, String fileName) {
    ActionHint hint = quickFix.configure(fileName);
    ((IntentionManagerImpl)IntentionManager.getInstance())
      .withDisabledIntentions(() -> quickFix.checkAction(fileName, hint));
  }

  @TestApplication
  public static class AddAllTest {
    private static final String TEST_DATA_PATH = testDataPath("addAll");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class AfterAllActionsTest {
    private static final String TEST_DATA_PATH = testDataPath("afterAllActions");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class AllMatchTest {
    private static final String TEST_DATA_PATH = testDataPath("allMatch");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class AnyMatchTest {
    private static final String TEST_DATA_PATH = testDataPath("anyMatch");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class BufferedReaderTest {
    private static final String TEST_DATA_PATH = testDataPath("bufferedReader");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class CollectTest {
    private static final String TEST_DATA_PATH = testDataPath("collect");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class ContinueTest {
    private static final String TEST_DATA_PATH = testDataPath("continue");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class CountTest {
    private static final String TEST_DATA_PATH = testDataPath("count");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class FilterTest {
    private static final String TEST_DATA_PATH = testDataPath("filter");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class FindFirstTest {
    private static final String TEST_DATA_PATH = testDataPath("findFirst");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class FlatMapFirstTest {
    private static final String TEST_DATA_PATH = testDataPath("flatMap");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class ForEachTest {
    private static final String TEST_DATA_PATH = testDataPath("foreach");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class JoiningTest {
    private static final String TEST_DATA_PATH = testDataPath("joining");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class LimitTest {
    private static final String TEST_DATA_PATH = testDataPath("limit");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class MinMaxTest {
    private static final String TEST_DATA_PATH = testDataPath("minMax");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class NoneMatchTest {
    private static final String TEST_DATA_PATH = testDataPath("noneMatch");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class OtherTest {
    private static final String TEST_DATA_PATH = testDataPath("other");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class ReductionTest {
    private static final String TEST_DATA_PATH = testDataPath("reduce");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class SummingTest {
    private static final String TEST_DATA_PATH = testDataPath("sum");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class Java9Test {
    private static final String TEST_DATA_PATH = testDataPath("java9");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class Java10Test {
    private static final String TEST_DATA_PATH = testDataPath("java10");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class Java14Test {
    private static final String TEST_DATA_PATH = testDataPath("java14");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }

  @TestApplication
  public static class Java19Test {
    private static final String TEST_DATA_PATH = testDataPath("java19");
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));
    private final TestFixture<LightQuickFixFixture> quickFixFixture = createQuickFixFixture(projectFixture, TEST_DATA_PATH);

    static List<String> files() {
      return lightQuickFixTestFiles(TEST_DATA_PATH);
    }

    @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
    @MethodSource("files")
    void parameterized(String fileName) {
      doTest(quickFixFixture.get(), fileName);
    }
  }
}
