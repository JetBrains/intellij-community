// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CharTailType extends TailType {
  private final char myChar;
  private final boolean myOverwrite;

  public CharTailType(final char aChar) {
    this(aChar, true);
  }

  public CharTailType(char aChar, boolean overwrite) {
    myChar = aChar;
    myOverwrite = overwrite;
  }

  @Override
  public boolean isApplicable(@NotNull InsertionContext context) {
    return !context.shouldAddCompletionChar() || context.getCompletionChar() != myChar;
  }

  @Override
  public int processTail(final Editor editor, final int tailOffset) {
    return insertChar(editor, tailOffset, myChar, myOverwrite);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof CharTailType that)) return false;

    if (myChar != that.myChar) return false;

    return true;
  }

  public int hashCode() {
    return myChar;
  }

  public @NonNls String toString() {
    return "CharTailType:'" + myChar + "'";
  }
}
