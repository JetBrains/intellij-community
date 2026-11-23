// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class IntroduceConstantMultiFileTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/introduceConstant/multifile/");

  @Test
  public void testStarImport() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass starImportClass = myFixture.findClass("StarImport");
      assertNotNull(starImportClass, "StarImport class not found");

      PsiFile containingFile = starImportClass.getContainingFile();
      VirtualFile virtualFile = containingFile.getVirtualFile();
      assertNotNull(virtualFile);
      myFixture.configureFromExistingVirtualFile(virtualFile);

      new MockIntroduceConstantHandler(null).invoke(myFixture.getProject(), myFixture.getEditor(), containingFile, null);
    });
  }
}
