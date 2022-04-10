// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class ApplicationRunLineMarkerProvider extends RunLineMarkerContributor {
  @Override
  public final @Nullable Info getInfo(@NotNull final PsiElement e) {
    if (Registry.is("ide.jvm.run.marker") || !isIdentifier(e)) {
      return null;
    }

    PsiElement element = e.getParent();
    PsiFile containingFile = element.getContainingFile();
    if (element instanceof PsiClass && PsiMethodUtil.findMainInClass((PsiClass)element) != null ||
        element instanceof PsiMethod && "main".equals(((PsiMethod)element).getName()) && PsiMethodUtil.isMainMethod((PsiMethod)element)) {
      if (JavaHighlightUtil.isJavaHashBangScript(containingFile)) {
        return null;
      }

      AnAction[] actions = ExecutorAction.getActions(Integer.MAX_VALUE);
      return new Info(AllIcons.RunConfigurations.TestState.Run, actions, element1 -> {
        return Arrays.stream(actions)
          .map(action -> getText(action, element1))
          .filter(Objects::nonNull)
          .collect(Collectors.joining("\n"));
      });
    }
    return null;
  }

  protected boolean isIdentifier(@NotNull PsiElement e) {
    return e instanceof PsiIdentifier;
  }
}
