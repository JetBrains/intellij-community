package com.intellij.openapi.diff.impl.fragments;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.EmptyIterator;

import java.util.Iterator;

public interface FragmentList {
  FragmentList shift(TextRange rangeShift1, TextRange rangeShift2,
                            int startLine1, int startLine2);

  FragmentList EMPTY = new FragmentList() {
    public FragmentList shift(TextRange rangeShift1, TextRange rangeShift2, int startLine1, int startLine2) {
      return EMPTY;
    }

    public boolean isEmpty() {
      return true;
    }

    public Iterator<Fragment> iterator() {
      return EmptyIterator.getInstance();
    }

    public Fragment getFragmentAt(int offset, FragmentSide side, Condition<Fragment> condition) {
      return null;
    }
  };

  boolean isEmpty();

  Iterator<Fragment> iterator();

  Fragment getFragmentAt(int offset, FragmentSide side, Condition<Fragment> condition);
}
