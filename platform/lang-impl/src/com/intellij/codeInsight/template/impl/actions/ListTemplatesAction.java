
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.template.impl.ListTemplatesHandler;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class ListTemplatesAction extends BaseCodeInsightAction implements DumbAware {
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new ListTemplatesHandler();
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }
}