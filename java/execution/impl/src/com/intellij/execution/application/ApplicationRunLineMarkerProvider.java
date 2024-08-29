// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.execution.ApplicationRunLineMarkerHider;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApplicationRunLineMarkerProvider extends RunLineMarkerContributor {

  @Override
  public boolean isDumbAware() {
    return this.getClass().isAssignableFrom(ApplicationRunLineMarkerProvider.class);
  }

  @Override
  public final @Nullable Info getInfo(@NotNull final PsiElement element) {
    return getInfoInner(element);
  }

  private @Nullable Info getInfoInner(@NotNull PsiElement element) {
    if (Registry.is("ide.jvm.run.marker") ||
        !isIdentifier(element) ||
        ApplicationRunLineMarkerHider.shouldHideRunLineMarker(element)) {
      return null;
    }

    PsiElement parent = element.getParent();
    if (parent instanceof PsiClass aClass) {
      if (!PsiMethodUtil.hasMainInClass(aClass)) return null;
      if (PsiTreeUtil.getParentOfType(aClass, PsiImplicitClass.class) != null) return null;
    }
    else if (parent instanceof PsiMethod method) {
      if (!"main".equals(method.getName()) || !PsiMethodUtil.isMainMethod(method)) return null;
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return null;
      if (!(containingClass instanceof PsiImplicitClass) && PsiTreeUtil.getParentOfType(containingClass, PsiImplicitClass.class) != null) {
        return null;
      }
      if (!PsiMethodUtil.MAIN_CLASS.value(containingClass)) return null;
      PsiMethod candidateMainMethod = PsiMethodUtil.findMainMethodInClassOrParent(containingClass);
      if (candidateMainMethod != method) return null;
    }
    else {
      return null;
    }
    if (JavaHighlightUtil.isJavaHashBangScript(element.getContainingFile())) {
      return null;
    }

    AnAction[] actions = ExecutorAction.getActions(Integer.MAX_VALUE);
    return new Info(AllIcons.RunConfigurations.TestState.Run, actions);
  }

  protected boolean isIdentifier(@NotNull PsiElement e) {
    return e instanceof PsiIdentifier;
  }
}
