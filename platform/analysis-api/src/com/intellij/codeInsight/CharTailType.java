// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Use {@link TailTypes#charType(char)} factory method instead of constructor to avoid possible deadlock
 * until deprecated static fields are not removed from {@link TailType},
 */
public class CharTailType extends ModNavigatorTailType {
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
  public int processTail(@NotNull Project project, @NotNull ModNavigator navigator, int tailOffset) {
    return insertChar(navigator, tailOffset, myChar, myOverwrite);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof CharTailType that)) return false;

    if (myChar != that.myChar) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myChar;
  }

  @Override
  public @NonNls String toString() {
    return "CharTailType:'" + myChar + "'";
  }
}
