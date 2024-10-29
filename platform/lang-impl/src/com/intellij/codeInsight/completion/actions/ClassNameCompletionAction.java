// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ClassNameCompletionAction extends BaseCodeCompletionAction{

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    invokeCompletion(e, CompletionType.BASIC, 2);
  }


}
