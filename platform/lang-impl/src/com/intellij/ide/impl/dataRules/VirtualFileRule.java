// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.*;

final class VirtualFileRule {
  static @Nullable VirtualFile getData(@NotNull DataMap dataProvider) {
    // Try to detect multi-selection.
    PsiElement[] psiElements = dataProvider.get(PSI_ELEMENT_ARRAY);
    if (psiElements != null) {
      for (PsiElement elem : psiElements) {
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(elem);
        if (virtualFile != null) return virtualFile;
      }
    }

    VirtualFile[] virtualFiles = dataProvider.get(VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null && virtualFiles.length == 1) {
      return virtualFiles[0];
    }

    PsiFile psiFile = dataProvider.get(PSI_FILE);
    if (psiFile != null) {
      return psiFile.getVirtualFile();
    }
    PsiElement elem = dataProvider.get(PSI_ELEMENT);
    if (elem == null) {
      return null;
    }
    return PsiUtilCore.getVirtualFile(elem);
  }
}
