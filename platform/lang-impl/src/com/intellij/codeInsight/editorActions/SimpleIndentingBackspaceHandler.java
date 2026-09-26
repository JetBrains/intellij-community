// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.psi.PsiFile;

final class SimpleIndentingBackspaceHandler extends AbstractIndentingBackspaceHandler {
  private LogicalPosition myTargetPosition;

  SimpleIndentingBackspaceHandler() {
    super(SmartBackspaceMode.INDENT);
  }

  @Override
  protected void doBeforeCharDeleted(char c, PsiFile file, Editor editor) {
    myTargetPosition = BackspaceHandler.getBackspaceUnindentPosition(file, editor);
  }

  @Override
  protected boolean doCharDeleted(char c, PsiFile file, Editor editor) {
    if (myTargetPosition != null) {
      BackspaceHandler.deleteToTargetPosition(editor, myTargetPosition);
      return true;
    }
    return false;
  }
}
