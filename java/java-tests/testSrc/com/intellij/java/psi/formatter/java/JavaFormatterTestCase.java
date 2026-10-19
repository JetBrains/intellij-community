// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.IOException;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.workspaceModel.ide.legacyBridge.impl.java.JavaModuleTypeUtils.JAVA_MODULE_ENTITY_TYPE_ID_NAME;

public abstract class JavaFormatterTestCase {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture =
    codeInsightModuleFixture(projectFixture, JAVA_MODULE_ENTITY_TYPE_ID_NAME);

  private Project project;
  private String testMethodName;

  @BeforeEach
  void setUpJavaFormatterTestCase(TestInfo testInfo) {
    project = codeInsightFixture.get().getProject();
    FormatterTestUtil.assertNoProjectCodeStyle(project);
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
    setUpMockJdk17(project, codeInsightFixture.get().getModule(), getTestRootDisposable());
  }

  private static void setUpMockJdk17(@NotNull Project project, @NotNull Module module, @NotNull Disposable disposable) {
    Sdk jdk = IdeaTestUtil.getMockJdk17();
    WriteAction.runAndWait(() -> {
      ProjectJdkTable.getInstance(project).addJdk(jdk, disposable);
      ProjectRootManager.getInstance(project).setProjectSdk(jdk);
      ModuleRootModificationUtil.setModuleSdk(module, jdk);
    });
    IndexingTestUtil.waitUntilIndexesAreReady(project);
  }

  protected abstract String getBasePath();

  protected @NotNull Project getProject() {
    return project;
  }

  protected @NotNull Disposable getTestRootDisposable() {
    return codeInsightFixture.get().getTestRootDisposable();
  }

  protected @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(testMethodName, lowercaseFirstLetter);
  }

  protected String getFileExtension() {
    return "java";
  }

  protected @NotNull String getTestDataDir() {
    return JavaTestUtil.getJavaTestDataPath() + File.separatorChar + getBasePath();
  }

  /**
   * Changes the text of each loaded test data file, as {@code FormatterTestCase.prepareText} did.
   */
  protected String prepareText(String text) {
    return text;
  }

  protected @NotNull String loadFile(@NotNull String name) throws IOException {
    return prepareText(FormatterTestUtil.loadFile(getTestDataDir(), name, null));
  }

  protected void doTest() throws IOException {
    String testName = getTestName(true);
    doTest(testName, testName + "_after");
  }

  protected void doTest(String fileNameBefore, String fileNameAfter) throws IOException {
    doTextTest(loadFile(fileNameBefore + "." + getFileExtension()), loadFile(fileNameAfter + "." + getFileExtension()));
  }

  protected void doTextTest(String text, String textAfter) {
    FormatterTestUtil.doTextTest(project, getFileExtension(), text, textAfter);
  }

  protected @NotNull CodeStyleSettings getSettings() {
    return CodeStyle.getSettings(project);
  }

  protected @NotNull CommonCodeStyleSettings getSettings(@NotNull Language language) {
    return getSettings().getCommonSettings(language);
  }

  protected JavaCodeStyleSettings getCustomJavaSettings() {
    return JavaCodeStyleSettings.getInstance(project);
  }
}
