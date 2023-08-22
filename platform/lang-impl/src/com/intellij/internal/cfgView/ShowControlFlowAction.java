// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.cfgView;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import org.jetbrains.annotations.NotNull;

public final class ShowControlFlowAction extends BaseCodeInsightAction {

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new ShowControlFlowHandler();
  }
}
