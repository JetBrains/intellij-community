// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.ImplementMethodsHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

public final class ImplementMethodsAction extends PresentableActionHandlerBasedAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new ImplementMethodsHandler();
  }

  @NotNull
  @Override
  protected LanguageExtension<LanguageCodeInsightActionHandler> getLanguageExtension() {
    return CodeInsightActions.IMPLEMENT_METHOD;
  }
}