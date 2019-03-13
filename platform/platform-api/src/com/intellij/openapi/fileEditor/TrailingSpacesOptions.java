// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public abstract class TrailingSpacesOptions {
  private @Nullable TrailingSpacesOptions myDelegate;

  public void setDelegate(@Nullable TrailingSpacesOptions delegate) {
    myDelegate = delegate;
  }

  public final boolean isStripTrailingSpaces() {
    return getThisOrDelegate(options -> options.getStripTrailingSpaces(), false);
  }

  public final boolean isEnsureNewLineAtEOF() {
    return getThisOrDelegate(options -> options.getEnsureNewLineAtEOF(), false);
  }

  public boolean isChangedLinesOnly() {
    return getThisOrDelegate(options -> options.getChangedLinesOnly(), false);
  }

  public boolean isKeepTrailingSpacesOnCaretLine() {
    return getThisOrDelegate(options -> options.getKeepTrailingSpacesOnCaretLine(), true);
  }

  private boolean getThisOrDelegate(@NotNull Function<TrailingSpacesOptions, Boolean> optionGetter, boolean defaultValue) {
    Boolean result = optionGetter.apply(this);
    if (result != null) return result;
    result = myDelegate != null ? optionGetter.apply(myDelegate) : null;
    return result != null ? result : defaultValue;
  }

  @Nullable
  protected abstract Boolean getStripTrailingSpaces();

  @Nullable
  protected abstract Boolean getEnsureNewLineAtEOF();

  @Nullable
  protected abstract Boolean getChangedLinesOnly();

  @Nullable
  protected abstract Boolean getKeepTrailingSpacesOnCaretLine();
}
