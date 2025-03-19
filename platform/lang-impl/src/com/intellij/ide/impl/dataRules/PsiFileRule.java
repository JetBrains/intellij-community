// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PsiFileRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataProvider);
    if (element != null) {
      return element.getContainingFile();
    }
    Project project = CommonDataKeys.PROJECT.getData(dataProvider);
    if (project != null) {
      VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataProvider);
      if (vFile != null && vFile.isValid()) {
        return PsiManager.getInstance(project).findFile(vFile);
      }
    }
    return null;
  }
}
