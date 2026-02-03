// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.hint.actions.NextParameterAction;
import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

// Prevents 'tab out' action from taking preference when caret is before completed method call's closing parenthesis
public final class JavaNextParameterActionPromoter implements ActionPromoter {
  @Override
  public @Unmodifiable List<AnAction> promote(@NotNull @Unmodifiable List<? extends AnAction> actions, @NotNull DataContext context) {
    if (!CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION) return null;
    if (ContainerUtil.findInstance(actions, NextParameterAction.class) == null) return null;
    // `NextParameterAction` will delegate to `BraceOrQuoteOutAction` (`PrevNextParameterHandler.doExecute`)
    return ContainerUtil.filter(actions, action -> !(action instanceof BraceOrQuoteOutAction));
  }
}
