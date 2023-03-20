// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiEditorUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.formatting.CaretPositionKeeper.POSITION_KEEPER_KEY;

public class EditorFormatterListener implements CodeStyleManager.Listener {

  @Override
  public void beforeReformatText(@NotNull PsiFile file) {
    Editor editor = PsiEditorUtil.findEditor(file);
    if (editor != null) {
      CaretPositionKeeper caretKeeper = new CaretPositionKeeper(editor, CodeStyle.getSettings(file), file.getLanguage());
      file.putUserData(POSITION_KEEPER_KEY, caretKeeper);
    }
  }

  @Override
  public void afterReformatText(@NotNull PsiFile file) {
    CaretPositionKeeper caretKeeper = file.getUserData(POSITION_KEEPER_KEY);
    if (caretKeeper != null) {
      file.putUserData(POSITION_KEEPER_KEY, null);
      caretKeeper.restoreCaretPosition();
    }
  }
}
