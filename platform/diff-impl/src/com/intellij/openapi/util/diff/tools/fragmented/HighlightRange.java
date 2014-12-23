package com.intellij.openapi.util.diff.tools.fragmented;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.diff.util.Side;
import org.jetbrains.annotations.NotNull;

class HighlightRange {
  @NotNull private final TextRange myBase;
  @NotNull private final TextRange myChanged;
  @NotNull private final Side mySide;

  public HighlightRange(@NotNull Side side, @NotNull TextRange base, @NotNull TextRange changed) {
    mySide = side;
    myBase = base;
    myChanged = changed;
  }

  @NotNull
  public Side getSide() {
    return mySide;
  }

  @NotNull
  public TextRange getBase() {
    return myBase;
  }

  @NotNull
  public TextRange getChanged() {
    return myChanged;
  }
}
