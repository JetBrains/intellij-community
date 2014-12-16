package com.intellij.openapi.util.diff.fragments;

import com.intellij.openapi.util.diff.util.ThreeSide;
import org.jetbrains.annotations.NotNull;

public interface MergeLineFragment {
  int getStartLine(@NotNull ThreeSide side);

  int getEndLine(@NotNull ThreeSide side);
}
