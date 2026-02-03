// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.moveLeftRight;

import com.intellij.openapi.actionSystem.PerformWithDocumentsCommitted;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class MoveElementLeftAction extends EditorAction implements PerformWithDocumentsCommitted {
  public MoveElementLeftAction() {
    super(new MoveElementLeftRightActionHandler(true));
    setInjectedContext(true);
  }
}
