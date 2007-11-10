package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.Util;

class UniteSameType implements DiffCorrection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.processing.UniteSameType");
  public static final DiffCorrection INSTANCE = new UniteSameType();
  public DiffFragment[] correct(DiffFragment[] fragments) {
    return unitSameTypes(covertSequentialOneSideToChange(unitSameTypes(fragments)));
  }

  private DiffFragment[] unitSameTypes(DiffFragment[] fragments) {
    if (fragments.length < 2) return fragments;
    DiffCorrection.FragmentsCollector collector = new DiffCorrection.FragmentsCollector();
    DiffFragment previous = fragments[0];
    for (int i = 1; i < fragments.length; i++) {
      DiffFragment fragment = fragments[i];
      if (!fragment.isOneSide() && fragment.getText1().length() == 0 && fragment.getText2().length() == 0) continue;
      if (Util.isSameType(previous, fragment)) {
        previous = Util.unite(previous, fragment);
      } else {
        collector.add(previous);
        previous = fragment;
      }
    }
    collector.add(previous);
    return collector.toArray();
  }

  private DiffFragment[] covertSequentialOneSideToChange(DiffFragment[] fragments) {
    if (fragments.length < 2) return fragments;
    DiffCorrection.FragmentsCollector collector = new DiffCorrection.FragmentsCollector();
//    DiffFragment previous = fragments[0];
    DiffFragment previous = null;
    for (int i = 0; i < fragments.length; i++) {
      DiffFragment fragment = fragments[i];
      if (fragment.isOneSide()) {
        if (previous == null) previous = fragment;
        else {
          FragmentSide side = FragmentSide.chooseSide(fragment);
          String previousText = side.getText(previous);
          if (previousText == null) previousText = "";
          previous = side.createFragment(previousText + side.getText(fragment), side.getOtherText(previous), true);
        }
      } else {
        if (previous != null) collector.add(previous);
        previous = null;
        collector.add(fragment);
      }
    }
    if (previous != null) collector.add(previous);
    return collector.toArray();
  }

  public static DiffFragment uniteAll(DiffFragment[] fragments) {
    fragments = INSTANCE.correct(fragments);
    LOG.assertTrue(fragments.length == 1);
    return fragments[0];
  }
}
