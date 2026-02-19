// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.actions;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public final class LfeEditorActionSearchBackHandler extends LfeEditorActionSearchAgainHandler {

  public LfeEditorActionSearchBackHandler(EditorActionHandler originalHandler) {
    super(originalHandler);
  }

  @Override
  protected boolean isForwardDirection() {
    return false;
  }
}
