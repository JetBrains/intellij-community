// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class LightResolveTestCase extends LightCodeInsightFixtureTestCase {
  protected PsiReference findReferenceAtCaret(@NotNull String filePath) {
    PsiFile file = myFixture.configureByFile(filePath);
    return file.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath() + "/psi/resolve/";
  }
}