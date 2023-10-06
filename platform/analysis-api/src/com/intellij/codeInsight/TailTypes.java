// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public final class TailTypes {
  private TailTypes() { }

  public static final TailType UNKNOWN = new TailType() {
    @Override
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }

    public String toString() {
      return "UNKNOWN";
    }
  };

  public static final TailType NONE = new TailType() {
    @Override
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }

    public String toString() {
      return "NONE";
    }
  };

  public static final TailType SEMICOLON = new CharTailType(';');

  /**
   * insert a space, overtype if already present
   */
  public static final TailType SPACE = new CharTailType(' ');

  /**
   * always insert a space
   */
  public static final TailType INSERT_SPACE = new CharTailType(' ', false);

  /**
   * insert a space unless there's one at the caret position already, followed by a word or '@'
   */
  public static final TailType HUMBLE_SPACE_BEFORE_WORD = new CharTailType(' ', false) {
    @Override
    public boolean isApplicable(@NotNull InsertionContext context) {
      CharSequence text = context.getDocument().getCharsSequence();
      int tail = context.getTailOffset();
      if (text.length() > tail + 1 && text.charAt(tail) == ' ') {
        char ch = text.charAt(tail + 1);
        if (ch == '@' || Character.isLetter(ch)) {
          return false;
        }
      }
      return super.isApplicable(context);
    }

    @Override
    public String toString() {
      return "HUMBLE_SPACE_BEFORE_WORD";
    }
  };

  public static final TailType DOT = new CharTailType('.');

  public static final TailType CASE_COLON = new CharTailType(':');

  public static final TailType EQUALS = new CharTailType('=');

  public static final TailType COND_EXPR_COLON = new TailType() {
    @Override
    public int processTail(final Editor editor, final int tailOffset) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      CharSequence chars = document.getCharsSequence();

      int afterWhitespace = CharArrayUtil.shiftForward(chars, tailOffset, " \n\t");
      if (afterWhitespace < textLength && chars.charAt(afterWhitespace) == ':') {
        return moveCaret(editor, tailOffset, afterWhitespace - tailOffset + 1);
      }
      document.insertString(tailOffset, " : ");
      return moveCaret(editor, tailOffset, 3);
    }

    public String toString() {
      return "COND_EXPR_COLON";
    }
  };
}
