package com.intellij.openapi.util.diff.fragments;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LineFragments {
  @NotNull private final List<? extends LineFragment> myFragments;
  private final boolean myFine;

  public LineFragments(@NotNull List<? extends LineFragment> fragments, boolean isFine) {
    myFragments = fragments;
    myFine = isFine;
  }

  @NotNull
  public List<? extends LineFragment> getFragments() {
    return myFragments;
  }

  public List<? extends FineLineFragment> getFineFragments() {
    //noinspection unchecked
    return myFine ? (List<? extends FineLineFragment>)myFragments : null;
  }

  public boolean isFine() {
    return myFine;
  }

  //
  // Constructors
  //

  @NotNull
  public static LineFragments create(@NotNull List<? extends LineFragment> fragments) {
    return new LineFragments(fragments, false);
  }

  @NotNull
  public static LineFragments createFine(@NotNull List<? extends FineLineFragment> fragments) {
    return new LineFragments(fragments, true);
  }
}
