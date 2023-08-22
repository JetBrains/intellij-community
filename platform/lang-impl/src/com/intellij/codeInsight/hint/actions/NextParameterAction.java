// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.hint.PrevNextParameterHandler;
import com.intellij.openapi.actionSystem.PerformWithDocumentsCommitted;
import com.intellij.openapi.editor.actionSystem.EditorAction;

public final class NextParameterAction extends EditorAction implements PerformWithDocumentsCommitted {
  public NextParameterAction() {
    super(new PrevNextParameterHandler(true));
    setInjectedContext(true);
  }
}
