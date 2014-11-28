package com.intellij.openapi.util.diff.tools.util;

import org.jetbrains.annotations.NotNull;

public interface PrevNextDifferenceIterable {
  void notify(@NotNull String message);

  boolean canGoPrev();

  boolean canGoNext();

  void goPrev();

  void goNext();
}
