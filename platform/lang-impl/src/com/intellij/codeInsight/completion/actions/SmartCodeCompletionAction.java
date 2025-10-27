// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SmartCodeCompletionAction extends BaseCodeCompletionAction implements ActionRemoteBehaviorSpecification {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    invokeCompletion(e, CompletionType.SMART, 1);
  }

  @Override
  @ApiStatus.Internal
  public @Nullable ActionRemoteBehavior getBehavior() {
    return CodeCompletionAction.getCompletionBehavior();
  }
}
