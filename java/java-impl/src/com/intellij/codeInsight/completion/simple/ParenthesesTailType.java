// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.completion.simple;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.editorActions.TabOutScopesTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

/**
 * @author peter
 */
public abstract class ParenthesesTailType extends TailType {

  protected abstract boolean isSpaceBeforeParentheses(CommonCodeStyleSettings styleSettings, Editor editor, final int tailOffset);

  protected abstract boolean isSpaceWithinParentheses(CommonCodeStyleSettings styleSettings, Editor editor, final int tailOffset);

  @Override
  public int processTail(final Editor editor, int tailOffset) {
    CommonCodeStyleSettings styleSettings = getLocalCodeStyleSettings(editor, tailOffset);
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