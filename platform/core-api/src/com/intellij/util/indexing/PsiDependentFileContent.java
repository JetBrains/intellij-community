// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.lang.LighterAST;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface PsiDependentFileContent extends FileContent {
  @Override
  @NotNull
  PsiFile getPsiFile();

  @NotNull
  LighterAST getLighterAST();
}
