package com.intellij.codeInsight;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public class TailTypeEx {
  public static final TailType SMART_LPARENTH = new TailType() {
    public int processTail(final Editor editor, int tailOffset) {
      tailOffset = insertChar(editor, tailOffset, '(');
      return !CodeInsightSettings.getInstance().INSERT_SINGLE_PARENTH
             ? moveCaret(editor, insertChar(editor, tailOffset, ')'), -1)
             : tailOffset;
    }

    @NonNls
    public String toString() {
      return "SMART_LPARENTH";
    }
  };

  private TailTypeEx() {
  }
}
