// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class SimpleCodeInsightAction extends CodeInsightAction implements CodeInsightActionHandler {

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return this;
  }
}
