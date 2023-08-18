// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testIntegration.JavaTestFramework;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class JUnitTestFramework extends JavaTestFramework {
  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.RunConfigurations.Junit;
  }

  @Override
  public boolean isTestMethod(PsiElement element, boolean checkAbstract) {
    if (element instanceof PsiMethod) {
      if (!isFrameworkAvailable(element)) return false;

      PsiClass containingClass = ((PsiMethod)element).getContainingClass();
      return containingClass != null && isTestClass(containingClass, false) &&
             JUnitUtil.getTestMethod(element, checkAbstract) != null;
    }
    return false;
  }

  @Override
  public boolean isMyConfigurationType(@NotNull ConfigurationType type) {
    return "JUnit".equals(type.getId());
  }

  @Override
  public boolean isTestMethod(PsiMethod method, PsiClass myClass) {
    return JUnitUtil.isTestMethod(MethodLocation.elementInClass(method, myClass));
  }
  
  public boolean shouldRunSingleClassAsJUnit5(Project project, GlobalSearchScope scope) {
    return false;
  }

}
