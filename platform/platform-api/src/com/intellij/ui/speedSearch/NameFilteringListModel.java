/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ui.speedSearch;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;

import javax.swing.*;

/**
 * @param <T> list elements generic type
 *
 * @author max
 * @author Konstantin Bulenkov
 */
public class NameFilteringListModel<T> extends FilteringListModel<T> {
  private final Function<? super T, String> myNamer;
  private int myFullMatchIndex = -1;
  private int myStartsWithIndex = -1;
  private final Computable<String> myPattern;

  /** @deprecated explicitly sets model for a list. Use other constructors instead. */
  @Deprecated(forRemoval = true)
  public NameFilteringListModel(JList<T> list,
                                Function<? super T, String> namer,
                                Condition<? super String> filter,
                                SpeedSearchSupply speedSearch) {
    this(list.getModel(), namer, filter, () -> StringUtil.notNullize(speedSearch.getEnteredPrefix()));
    list.setModel(this);
  }

  public NameFilteringListModel(ListModel<T> model,
                                Function<? super T, String> namer,
                                Condition<? super String> filter,
                                Computable<String> pattern) {
    super(model);
    myPattern = pattern;
    myNamer = namer;
    setFilter(namer != null ? t -> filter.value(namer.fun(t)) : null);
  }

  @Override
  protected void addToFiltered(T elt) {
    super.addToFiltered(elt);

    if (myNamer != null) {
      String name = myNamer.fun(elt);
      if (name != null) {
        String filterString = StringUtil.toUpperCase(myPattern.compute());
        String candidateString = StringUtil.toUpperCase(name);
        int index = getSize() - 1;

        if (myFullMatchIndex == -1 && filterString.equals(candidateString)) {
          myFullMatchIndex = index;
        }

        if (myStartsWithIndex == -1 && candidateString.startsWith(filterString)) {
          myStartsWithIndex = index;
        }
      }
    }
  }

  @Override
  public void refilter() {
    myFullMatchIndex = -1;
    myStartsWithIndex = -1;
    super.refilter();
  }

  public int getClosestMatchIndex() {
    return myFullMatchIndex != -1 ? myFullMatchIndex : myStartsWithIndex;
  }
}
