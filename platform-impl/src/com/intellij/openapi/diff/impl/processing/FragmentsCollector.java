package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.InlineFragment;
import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;

class FragmentsCollector {
  private final ArrayList<Fragment> myFragments = new ArrayList<Fragment>();
  private int myOffset1 = 0;
  private int myOffset2 = 0;

  public Fragment addDiffFragment(DiffFragment fragment) {
    int length1 = LineFragmentsCollector.getLength(fragment.getText1());
    int length2 = LineFragmentsCollector.getLength(fragment.getText2());
    InlineFragment inlineFragment = new InlineFragment(LineFragmentsCollector.getType(fragment),
                                         new TextRange(myOffset1, myOffset1 + length1),
                                         new TextRange(myOffset2, myOffset2 + length2));
    myFragments.add(inlineFragment);
    myOffset1 += length1;
    myOffset2 += length2;
    return inlineFragment;
  }

  public ArrayList<Fragment> getFragments() { return myFragments; }
}
