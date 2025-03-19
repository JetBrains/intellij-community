// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public final class JavadocLineStartWithSelectionHandler extends JavadocLineStartHandler {
  public JavadocLineStartWithSelectionHandler(EditorActionHandler originalHandler) {
    super(originalHandler, true);
  }
}
