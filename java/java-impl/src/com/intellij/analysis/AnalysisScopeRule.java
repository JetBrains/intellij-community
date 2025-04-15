// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.analysis;

import com.intellij.openapi.actionSystem.*;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public final class AnalysisScopeRule implements UiDataRule {
  @Override
  public void uiDataSnapshot(@NotNull DataSink sink, @NotNull DataSnapshot snapshot) {
    sink.lazyValue(AnalysisScopeUtil.KEY, dataProvider -> {
      if (dataProvider.get(CommonDataKeys.PSI_FILE) instanceof PsiJavaFile javaFile) {
        return new JavaAnalysisScope(javaFile);
      }
      Object psiTarget = dataProvider.get(CommonDataKeys.PSI_ELEMENT);
      if (psiTarget instanceof PsiPackage pack) {
        PsiManager manager = pack.getManager();
        if (!manager.isInProject(pack)) return null;
        PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(manager.getProject()));
        if (dirs.length == 0) return null;
        return new JavaAnalysisScope(pack, dataProvider.get(PlatformCoreDataKeys.MODULE));
      }
      return null;
    });
  }
}