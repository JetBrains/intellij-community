// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class ExtractMethodNewJspecifyMultiFileTest {
  private static final LightProjectDescriptor PROJECT_DESCRIPTOR =
    new DefaultLightProjectDescriptor(() -> IdeaTestUtil.getMockJdk11()).withRepositoryLibrary("org.jspecify:jspecify:1.0.0");
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(PROJECT_DESCRIPTOR);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/extractMethodNewJspecifyMultiFile/");

  @Test
  public void testNullMarkedPackage() {
    doTest();
  }

  @Test
  public void testNullMarkedModule() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> {
        PsiFile file = PsiTreeUtil.getParentOfType(myFixture.findClass("packag.Test"), PsiFile.class);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        ExtractMethodNewTest.performExtractMethod(true, false, myFixture.getEditor(), file, myFixture.getProject());
      }, null, null);
    });
  }
}
