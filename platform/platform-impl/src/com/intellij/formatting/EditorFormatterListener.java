// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.util.PsiEditorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class EditorFormatterListener implements CodeStyleManager.Listener {
  static final Key<EditorStateKeeper> EDITOR_STATE_KEY = Key.create("formatter.caret.position.keeper");

  @Override
  public void beforeReformatText(@NotNull PsiFile file) {
    Editor editor = PsiEditorUtil.findEditor(file);
    if (editor != null) {
      EditorStateKeeper editorStateKeeper = new EditorStateKeeper(editor, CodeStyle.getSettings(file), file.getLanguage());
      file.putUserData(EDITOR_STATE_KEY, editorStateKeeper);
    }
  }

  @Override
  public void afterReformatText(@NotNull PsiFile file) {
    EditorStateKeeper editorStateKeeper = file.getUserData(EDITOR_STATE_KEY);
    if (editorStateKeeper != null) {
      file.putUserData(EDITOR_STATE_KEY, null);
      editorStateKeeper.restoreState();
    }
  }

  private static final class EditorStateKeeper {
    CaretPositionKeeper                       myCaretPositionKeeper;
    EditorScrollingPositionKeeper.ForDocument myScrollingPositionKeeper;

    private EditorStateKeeper(@NotNull Editor editor, @NotNull CodeStyleSettings settings, @NotNull Language language) {
      myCaretPositionKeeper = new CaretPositionKeeper(editor, settings, language);
      myScrollingPositionKeeper = new EditorScrollingPositionKeeper.ForDocument(editor.getDocument());
      myScrollingPositionKeeper.savePosition();
    }

    void restoreState() {
      try {
        myCaretPositionKeeper.restoreCaretPosition();
        myScrollingPositionKeeper.restorePosition(true);
      }
      finally {
        Disposer.dispose(myScrollingPositionKeeper);
      }
    }
  }
}
