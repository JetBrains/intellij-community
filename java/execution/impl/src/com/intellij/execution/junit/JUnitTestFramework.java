// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.RunConfigurations.Junit;
  }

  @Override
  public boolean isTestMethod(PsiElement element, boolean checkAbstract) {
    if (element == null) return false;
    return callWithAlternateResolver(element.getProject(), () -> {
      if (element instanceof PsiMethod) {
        if (!isFrameworkAvailable(element)) return false;
        PsiClass containingClass = ((PsiMethod)element).getContainingClass();
        return containingClass != null && isTestClass(containingClass, false) &&
               JUnitUtil.getTestMethod(element, checkAbstract) != null;
      }
      return false;
    }, false);
  }

  @Override
  public boolean isMyConfigurationType(@NotNull ConfigurationType type) {
    return "JUnit".equals(type.getId());
  }

  @Override
  public boolean isTestMethod(PsiMethod method, PsiClass myClass) {
    if (method == null) return false;
    return callWithAlternateResolver(method.getProject(), () -> {
      return JUnitUtil.isTestMethod(MethodLocation.elementInClass(method, myClass));
    }, false);
  }
  
  public boolean shouldRunSingleClassAsJUnit5(Project project, GlobalSearchScope scope) {
    return false;
  }

}
