// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixOptions;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.PsiDocumentManagerEx;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class I18nQuickFixTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/i18n";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightQuickFixFixture> quickFixFixture =
    lightQuickFixFixture(projectFixture, TEST_DATA_PATH, List::of, LightQuickFixOptions.DEFAULT.withLanguageLevelFromFileName());

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @BeforeEach
  void setUpInspections() {
    I18nInspection inspection = new I18nInspection();
    inspection.setReportUnannotatedReferences(true);
    quickFixFixture.get().getFixture().enableInspections(inspection);
  }

  @AfterEach
  void clearUncommittedDocuments() {
    // avoid "memory/disk conflict" when the document for changed annotation.xml stays in memory
    ((PsiDocumentManagerEx)PsiDocumentManager.getInstance(quickFixFixture.get().getFixture().getProject())).clearUncommittedDocuments();
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    VfsGuardian.guard(FileUtil.toSystemIndependentName(PlatformTestUtil.getCommunityPath()), quickFixFixture.get().getFixture().getTestRootDisposable());
    LightQuickFixFixture quickFix = quickFixFixture.get();
    boolean mustBeAvailableAfterInvoke = Comparing.strEqual(fileName, "SystemCall.java");
    if (mustBeAvailableAfterInvoke) {
      quickFix = quickFix.withOptions(quickFix.getOptions().withActionAvailableAfterExecution());
    }
    quickFix.doTest(fileName);
  }
}
