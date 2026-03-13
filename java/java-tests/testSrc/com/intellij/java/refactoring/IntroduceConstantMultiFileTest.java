// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.LightMultiFileTestCase;
import org.jetbrains.annotations.NotNull;

public class IntroduceConstantMultiFileTest extends LightMultiFileTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/introduceConstant/multifile/";
  }

  public void testStarImport() {
    doTest(() -> {
      PsiClass starImportClass = myFixture.findClass("StarImport");
      assertNotNull("StarImport class not found", starImportClass);

      PsiFile containingFile = starImportClass.getContainingFile();
      VirtualFile virtualFile = containingFile.getVirtualFile();
      assertNotNull(virtualFile);
      myFixture.configureFromExistingVirtualFile(virtualFile);

      new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), containingFile, null);
    });
  }
}
