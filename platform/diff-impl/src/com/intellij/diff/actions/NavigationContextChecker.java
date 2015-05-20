/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.actions;

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
