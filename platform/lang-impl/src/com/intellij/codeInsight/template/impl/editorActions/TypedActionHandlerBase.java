// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.ActionPlan;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TypedActionHandlerBase implements TypedActionHandlerEx {
  protected final @Nullable TypedActionHandler myOriginalHandler;

  public TypedActionHandlerBase(@Nullable TypedActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public void beforeExecute(@NotNull Editor editor, char c, @NotNull DataContext context, @NotNull ActionPlan plan) {
    if (myOriginalHandler instanceof TypedActionHandlerEx) {
      ((TypedActionHandlerEx)myOriginalHandler).beforeExecute(editor, c, context, plan);
    }
  }
}
