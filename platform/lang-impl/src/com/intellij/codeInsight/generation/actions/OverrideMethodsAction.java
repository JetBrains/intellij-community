// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.OverrideMethodsHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

public final class OverrideMethodsAction extends PresentableActionHandlerBasedAction {

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new OverrideMethodsHandler();
  }

  @NotNull
  @Override
  protected LanguageExtension<LanguageCodeInsightActionHandler> getLanguageExtension() {
    return CodeInsightActions.OVERRIDE_METHOD;
  }
}