// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion.simple;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.editorActions.TabOutScopesTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public abstract class ParenthesesTailType extends TailType {

  protected abstract boolean isSpaceBeforeParentheses(CommonCodeStyleSettings styleSettings, Editor editor, final int tailOffset);

  protected abstract boolean isSpaceWithinParentheses(CommonCodeStyleSettings styleSettings, Editor editor, final int tailOffset);

  @Override
  public int processTail(final @NotNull Editor editor, int tailOffset) {
    CommonCodeStyleSettings styleSettings = CodeStyle.getLocalLanguageSettings(editor, tailOffset);
    if (isSpaceBeforeParentheses(styleSettings, editor, tailOffset)) {
      tailOffset = insertChar(editor, tailOffset, ' ');
    }
    Document document = editor.getDocument();
    if (tailOffset < document.getTextLength() && document.getCharsSequence().charAt(tailOffset) == '(') {
      return moveCaret(editor, tailOffset, 1);
    }

    tailOffset = insertChar(editor, tailOffset, '(');
    if (isSpaceWithinParentheses(styleSettings, editor, tailOffset)) {
      tailOffset = insertChar(editor, tailOffset, ' ');
      tailOffset = insertChar(editor, tailOffset, ' ');
      tailOffset = insertChar(editor, tailOffset, ')');
      moveCaret(editor, tailOffset, -2);
    } else {
      tailOffset = insertChar(editor, tailOffset, ')');
      moveCaret(editor, tailOffset, -1);
      TabOutScopesTracker.getInstance().registerEmptyScopeAtCaret(editor);
    }
    return tailOffset;
  }

}
