// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class PsiJavaModuleTreeChangePreprocessor implements PsiTreeChangePreprocessor {
  @Override
  public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    PsiFile file = event.getFile();
    if (file != null && PsiJavaModuleModificationTracker.isModuleFile(file.getName())) {
      PsiJavaModuleModificationTracker.getInstance(file.getProject()).incModificationCount();
    }
  }
}
