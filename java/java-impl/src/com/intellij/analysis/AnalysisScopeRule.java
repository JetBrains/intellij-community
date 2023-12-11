// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.analysis;

import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public final class AnalysisScopeRule implements GetDataRule {
  @Override
  public Object getData(@NotNull final DataProvider dataProvider) {
    if (dataProvider.getData(CommonDataKeys.PSI_FILE.getName()) instanceof PsiJavaFile javaFile) {
      return new JavaAnalysisScope(javaFile);
    }
    Object psiTarget = dataProvider.getData(CommonDataKeys.PSI_ELEMENT.getName());
    if (psiTarget instanceof PsiPackage pack) {
      PsiManager manager = pack.getManager();
      if (!manager.isInProject(pack)) return null;
      PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(manager.getProject()));
      if (dirs.length == 0) return null;
      return new JavaAnalysisScope(pack, (Module)dataProvider.getData(PlatformCoreDataKeys.MODULE.getName()));
    }
    return null;
  }
}