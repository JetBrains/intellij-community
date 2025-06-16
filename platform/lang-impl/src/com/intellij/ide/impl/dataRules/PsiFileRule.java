// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.CommonDataKeys.*;

final class PsiFileRule {
  static @Nullable PsiFile getData(@NotNull DataMap dataProvider) {
    final PsiElement element = dataProvider.get(PSI_ELEMENT);
    if (element != null) {
      return element.getContainingFile();
    }
    Project project = dataProvider.get(PROJECT);
    if (project != null) {
      VirtualFile vFile = dataProvider.get(VIRTUAL_FILE);
      if (vFile != null && vFile.isValid()) {
        return PsiManager.getInstance(project).findFile(vFile);
      }
    }
    return null;
  }
}
