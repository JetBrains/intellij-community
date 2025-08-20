// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class CodeCompletionAction extends BaseCodeCompletionAction implements LightEditCompatible {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    invokeCompletion(e, CompletionType.BASIC, 1);
  }
}
