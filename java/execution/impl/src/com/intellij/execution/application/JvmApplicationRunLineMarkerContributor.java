// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.jvm.JvmRunLineMarkerContributor;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.icons.AllIcons;
import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmElement;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

import static com.intellij.lang.jvm.util.JvmMainMethodUtil.hasMainMethodInHierarchy;
import static com.intellij.lang.jvm.util.JvmMainMethodUtil.isMainMethod;

public final class JvmApplicationRunLineMarkerContributor extends JvmRunLineMarkerContributor {
  @Override
  protected @Nullable Info getInfo(@NotNull PsiElement psiElement, @NotNull JvmElement element) {
    return showMarker(element) ? info(psiElement) : null;
  }

  private static boolean showMarker(@NotNull JvmElement declaration) {
    return declaration instanceof JvmMethod && isMainMethod((JvmMethod)declaration) ||
           declaration instanceof JvmClass && hasMainMethodInHierarchy((JvmClass)declaration);
  }

  @NotNull
  private static Info info(@NotNull PsiElement element) {
    AnAction[] actions = ExecutorAction.getActions(Integer.MAX_VALUE);
    AnActionEvent event = createActionEvent(element);
    Function<PsiElement, String> tooltipProvider = o -> StringUtil.join(ContainerUtil.mapNotNull(
      actions, action -> getText(action, event)), "\n");
    return new Info(AllIcons.RunConfigurations.TestState.Run, actions, tooltipProvider
    );
  }
}
