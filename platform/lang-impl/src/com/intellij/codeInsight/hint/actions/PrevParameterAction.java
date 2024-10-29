// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.hint.PrevNextParameterHandler;
import com.intellij.openapi.actionSystem.PerformWithDocumentsCommitted;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class PrevParameterAction extends EditorAction implements PerformWithDocumentsCommitted {
  public PrevParameterAction() {
    super(new PrevNextParameterHandler(false));
    setInjectedContext(true);
  }
}
