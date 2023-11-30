// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

public final class CompleteSmartMacro extends BaseCompleteMacro {
  public CompleteSmartMacro() {
    super("completeSmart");
  }

  @Override
  protected void invokeCompletionHandler(Project project, Editor editor) {
    CodeCompletionHandlerBase.createHandler(CompletionType.SMART).invokeCompletion(project, editor, 1);
  }
}