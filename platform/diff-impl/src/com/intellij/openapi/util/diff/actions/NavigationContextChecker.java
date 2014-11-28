package com.intellij.openapi.util.diff.actions;

import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public class NavigationContextChecker {
  @NotNull private final Iterator<Pair<Integer, CharSequence>> myChangedLinesIterator;
  @NotNull private final DiffNavigationContext myContext;

  public NavigationContextChecker(@NotNull Iterator<Pair<Integer, CharSequence>> changedLinesIterator,
                                  @NotNull DiffNavigationContext context) {
    myChangedLinesIterator = changedLinesIterator;
    myContext = context;
  }

  public int contextMatchCheck() {
    // we ignore spaces.. at least at start/end, since some version controls could ignore their changes when doing annotate
    Iterator<? extends CharSequence> iterator = myContext.getPreviousLinesIterable().iterator();

    if (iterator.hasNext()) {
      CharSequence contextLine = iterator.next();

      while (myChangedLinesIterator.hasNext()) {
        Pair<Integer, ? extends CharSequence> pair = myChangedLinesIterator.next();
        if (StringUtil.equalsTrimWhitespaces(pair.getSecond(), contextLine)) {
          if (!iterator.hasNext()) break;
          contextLine = iterator.next();
        }
      }
    }
    if (iterator.hasNext()) return -1;
    if (!myChangedLinesIterator.hasNext()) return -1;

    CharSequence targetLine = myContext.getTargetString();
    while (myChangedLinesIterator.hasNext()) {
      Pair<Integer, ? extends CharSequence> pair = myChangedLinesIterator.next();
      if (StringUtil.equalsTrimWhitespaces(pair.getSecond(), targetLine)) {
        return pair.getFirst();
      }
    }

    return -1;
  }
}
