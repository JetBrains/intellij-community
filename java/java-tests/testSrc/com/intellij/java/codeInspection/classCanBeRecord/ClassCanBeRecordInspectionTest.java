// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.classCanBeRecord;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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

/**
 * The files that end with {@code ValueClass.java} use the {@code JAVA_X} project, and the files that end with {@code Markdown.java} use the
 * {@code JAVA_23} project. The other files use the {@code JAVA_21} project.
 */
@TestApplication
public class ClassCanBeRecordInspectionTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/inspection/classCanBeRecord";
  private static final TestFixture<LightJavaProject> java21ProjectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private static final TestFixture<LightJavaProject> java23ProjectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private static final TestFixture<LightJavaProject> javaXProjectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_X);
  private final TestFixture<LightQuickFixFixture> java21QuickFixFixture =
    lightQuickFixFixture(java21ProjectFixture, TEST_DATA_PATH, ClassCanBeRecordInspectionTest::createInspections);
  private final TestFixture<LightQuickFixFixture> java23QuickFixFixture =
    lightQuickFixFixture(java23ProjectFixture, TEST_DATA_PATH, ClassCanBeRecordInspectionTest::createInspections);
  private final TestFixture<LightQuickFixFixture> javaXQuickFixFixture =
    lightQuickFixFixture(javaXProjectFixture, TEST_DATA_PATH, ClassCanBeRecordInspectionTest::createInspections);

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    LightQuickFixFixture quickFix = getQuickFixFixture(fileName);
    try {
      quickFix.doTest(fileName);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      // Verify that no content was changed. See IDEA-371645.
      quickFix.getFixture().checkResultByFile("before" + fileName);
    }


    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> quickFix.doTest(fileName));
  }

  private @NotNull LightQuickFixFixture getQuickFixFixture(@NotNull String fileName) {
    if (fileName.endsWith("ValueClass.java")) {
      return javaXQuickFixFixture.get();
    }
    if (fileName.endsWith("Markdown.java")) {
       return java23QuickFixFixture.get();
    }
    return java21QuickFixFixture.get();
  }

  private static @NotNull List<ClassCanBeRecordInspection> createInspections() {
    ClassCanBeRecordInspection inspection = new ClassCanBeRecordInspection(ConversionStrategy.DO_NOT_SUGGEST, true);
    inspection.myIgnoredAnnotations.add("my.annotation1.MyAnn");
    inspection.myIgnoredAnnotations.add("my.annotation2.*");
    return List.of(inspection);
  }
}
