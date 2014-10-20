package com.intellij.openapi.util.diff.fragments;

public interface LineFragment extends DiffFragment {
  int getStartLine1();

  int getEndLine1();

  int getStartLine2();

  int getEndLine2();
}
