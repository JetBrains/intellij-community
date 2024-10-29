// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public final class VirtualFileRule implements GetDataRule {
  @Override
  public Object getData(final @NotNull DataProvider dataProvider) {
    // Try to detect multi-selection.
    PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataProvider);
    if (psiElements != null) {
      for (PsiElement elem : psiElements) {
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(elem);
        if (virtualFile != null) return virtualFile;
      }
    }

    VirtualFile[] virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataProvider);
    if (virtualFiles != null && virtualFiles.length == 1) {
      return virtualFiles[0];
    }

    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataProvider);
    if (psiFile != null) {
      return psiFile.getVirtualFile();
    }
    PsiElement elem = CommonDataKeys.PSI_ELEMENT.getData(dataProvider);
    if (elem == null) {
      return null;
    }
    return PsiUtilCore.getVirtualFile(elem);
  }
}
