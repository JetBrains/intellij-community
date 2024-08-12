// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.OverrideMethodsHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public final class OverrideMethodsAction extends PresentableActionHandlerBasedAction implements DumbAware {

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new OverrideMethodsHandler();
  }

  @Override
  protected @NotNull LanguageExtension<LanguageCodeInsightActionHandler> getLanguageExtension() {
    return CodeInsightActions.OVERRIDE_METHOD;
  }
}