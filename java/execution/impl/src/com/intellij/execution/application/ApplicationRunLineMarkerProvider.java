// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.execution.ApplicationRunLineMarkerHider;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ApplicationRunLineMarkerProvider extends RunLineMarkerContributor {
  @Override
  public final @Nullable Info getInfo(@NotNull final PsiElement element) {
    if (Registry.is("ide.jvm.run.marker") ||
        !isIdentifier(element) ||
        ApplicationRunLineMarkerHider.shouldHideRunLineMarker(element)) {
      return null;
    }

    PsiElement parent = element.getParent();
    if (parent instanceof PsiClass aClass) {
      if (PsiMethodUtil.findMainInClass(aClass) == null) return null;
    }
    else if (parent instanceof PsiMethod method) {
      if (!"main".equals(method.getName()) || !PsiMethodUtil.isMainMethod(method)) return null;
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || PsiUtil.isLocalOrAnonymousClass(containingClass)) return null;
    }
    else {
      return null;
    }
    if (JavaHighlightUtil.isJavaHashBangScript(element.getContainingFile())) {
      return null;
    }

    AnAction[] actions = ExecutorAction.getActions(Integer.MAX_VALUE);
    return new Info(AllIcons.RunConfigurations.TestState.Run, actions, new ActionsTooltipProvider(actions));
  }

  private record ActionsTooltipProvider(@NotNull AnAction @NotNull [] myActions) implements Function<PsiElement, String> {
    @Override
    public String apply(PsiElement element) {
      return Arrays.stream(myActions)
            .map(action -> getText(action, element))
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n"));
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myActions);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ActionsTooltipProvider other && Arrays.equals(myActions, other.myActions);
    }
  }

  protected boolean isIdentifier(@NotNull PsiElement e) {
    return e instanceof PsiIdentifier;
  }
}
