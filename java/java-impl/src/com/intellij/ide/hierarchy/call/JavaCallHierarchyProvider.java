// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightDefaultConstructor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class JavaCallHierarchyProvider implements HierarchyProvider {
  @Override
  public PsiElement getTarget(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof PsiField || element instanceof PsiRecordComponent) return element;
    else if (element instanceof PsiClass aClass) {
      if (aClass.isRecord()) return element;
      PsiMethod defaultConstructor = LightDefaultConstructor.create(aClass);
      if (defaultConstructor != null) return defaultConstructor;
    }
    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
  }

  @Override
  public @NotNull HierarchyBrowser createHierarchyBrowser(@NotNull PsiElement target) {
    return new CallHierarchyBrowser(target.getProject(), (PsiMember)target);
  }

  @Override
  public void browserActivated(@NotNull HierarchyBrowser hierarchyBrowser) {
    ((CallHierarchyBrowser)hierarchyBrowser).changeView(CallHierarchyBrowserBase.getCallerType());
  }
}
