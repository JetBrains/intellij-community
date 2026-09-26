// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Dennis.Ushakov
 */
@ApiStatus.Internal
public final class MoveLineDownAction extends EditorAction {
  public MoveLineDownAction() {
    super(new MoveLineHandler(true));
  }
}
