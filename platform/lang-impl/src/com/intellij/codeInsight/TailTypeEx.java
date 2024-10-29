// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;


public final class TailTypeEx {
  public static final TailType SMART_LPARENTH = new TailType() {
    @Override
    public int processTail(final Editor editor, int tailOffset) {
      tailOffset = insertChar(editor, tailOffset, '(');
      return moveCaret(editor, insertChar(editor, tailOffset, ')'), -1);
    }

    public @NonNls String toString() {
      return "SMART_LPARENTH";
    }
  };

  private TailTypeEx() {
  }
}
