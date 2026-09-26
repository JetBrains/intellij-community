// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodNewJspecifyMultiFileTest extends LightMultiFileTestCase {
  private static final LightProjectDescriptor PROJECT_DESCRIPTOR =
    new DefaultLightProjectDescriptor(() -> IdeaTestUtil.getMockJdk11()).withRepositoryLibrary("org.jspecify:jspecify:1.0.0");

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return PROJECT_DESCRIPTOR;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/extractMethodNewJspecifyMultiFile/";
  }

  public void testNullMarkedPackage() {
    doTest();
  }

  public void testNullMarkedModule() {
    doTest();
  }

  private void doTest() {
    doTest(() -> {
      CommandProcessor.getInstance().executeCommand(getProject(), () -> {
        PsiFile file = PsiTreeUtil.getParentOfType(myFixture.findClass("packag.Test"), PsiFile.class);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        ExtractMethodNewTest.performExtractMethod(true, false, myFixture.getEditor(), file, myFixture.getProject());
      }, null, null);
    });
  }
}
