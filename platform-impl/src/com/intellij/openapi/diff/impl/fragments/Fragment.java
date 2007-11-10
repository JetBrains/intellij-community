package com.intellij.openapi.diff.impl.fragments;

import com.intellij.openapi.diff.impl.highlighting.DiffMarkup;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;

public interface Fragment {
  TextDiffType getType();
  TextRange getRange(FragmentSide side);

  Fragment shift(TextRange range1, TextRange range2, int startingLine1, int startingLine2);

  void highlight(DiffMarkup appender1, DiffMarkup appender2, boolean isLast);

  Fragment getSubfragmentAt(int offset, FragmentSide side, Condition<Fragment> condition);
}
