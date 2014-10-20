package com.intellij.openapi.util.diff.fragments;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FineLineFragmentImpl extends LineFragmentImpl implements FineLineFragment {
  @Nullable private final List<DiffFragment> myFragments;

  public FineLineFragmentImpl(@NotNull LineFragment fragment,
                              @Nullable List<DiffFragment> fragments) {
    super(fragment);
    myFragments = fragments;
  }

  public FineLineFragmentImpl(int startLine1, int endLine1, int startLine2, int endLine2,
                              int startOffset1, int endOffset1, int startOffset2, int endOffset2,
                              @Nullable List<DiffFragment> fragments) {
    super(startLine1, endLine1, startLine2, endLine2, startOffset1, endOffset1, startOffset2, endOffset2);
    myFragments = fragments;
  }

  @Override
  @Nullable
  public List<DiffFragment> getFineFragments() {
    return myFragments;
  }
}
