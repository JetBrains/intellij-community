package com.intellij.openapi.util.diff.fragments;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface FineLineFragment extends LineFragment {
  @Nullable
  List<DiffFragment> getFineFragments();
}
