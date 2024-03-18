// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public final class TailTypes {
  private TailTypes() { }

  private static final TailType UNKNOWN = new TailType() {
    @Override
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }

    public String toString() {
      return "UNKNOWN";
    }
  };

  private static final TailType NONE = new TailType() {
    @Override
    public int processTail(final Editor editor, final int tailOffset) {
      return tailOffset;
    }

    public String toString() {
      return "NONE";
    }
  };

  private static final TailType SEMICOLON = new CharTailType(';');

  private static final TailType SPACE = new CharTailType(' ');

  private static final TailType INSERT_SPACE = new CharTailType(' ', false);

  private static final TailType HUMBLE_SPACE_BEFORE_WORD = new CharTailType(' ', false) {
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

  private static final TailType DOT = new CharTailType('.');

  private static final TailType CASE_COLON = new CharTailType(':');

  private static final TailType EQUALS = new CharTailType('=');

  private static final TailType COND_EXPR_COLON = new TailType() {
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

  public static TailType unknownType() {
    return UNKNOWN;
  }

  public static TailType noneType() {
    return NONE;
  }

  public static TailType semicolonType() {
    return SEMICOLON;
  }

  /**
   * insert a space, overtype if already present
   */
  public static TailType spaceType() {
    return SPACE;
  }

  /**
   * always insert a space
   */
  public static TailType insertSpaceType() {
    return INSERT_SPACE;
  }

  /**
   * insert a space unless there's one at the caret position already, followed by a word or '@'
   */
  public static TailType humbleSpaceBeforeWordType() {
    return HUMBLE_SPACE_BEFORE_WORD;
  }

  public static TailType dotType() {
    return DOT;
  }

  public static TailType caseColonType() {
    return CASE_COLON;
  }

  public static TailType equalsType() {
    return EQUALS;
  }

  public static TailType conditionalExpressionColonType() {
    return COND_EXPR_COLON;
  }

  public static TailType charType(char aChar) {
    return new CharTailType(aChar);
  }

  public static TailType charType(char aChar, boolean overwrite) {
    return new CharTailType(aChar, overwrite);
  }
}
