// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public class JavadocLineStartWithSelectionHandler extends JavadocLineStartHandler {
  public JavadocLineStartWithSelectionHandler(EditorActionHandler originalHandler) {
    super(originalHandler, true);
  }
}
