/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupArranger;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.lookup.impl.LookupItemWeightComparable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.WeighingService;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class CompletionLookupArranger extends LookupArranger {
  public static final Key<LookupItemWeightComparable> RELEVANCE_KEY = Key.create("RELEVANCE_KEY");
  private static final String SELECTED = "selected";
  static final String IGNORED = "ignored";
  private final CompletionLocation myLocation;
  public static final Key<Comparable[]> WEIGHT = Key.create("WEIGHT");
  private final Map<LookupElement, Comparable> mySortingWeights = new THashMap<LookupElement, Comparable>(TObjectHashingStrategy.IDENTITY);

  public CompletionLookupArranger(final CompletionParameters parameters) {
    myLocation = new CompletionLocation(parameters);
  }

  @Override
  public void sortItems(List<LookupElement> items) {
    Collections.sort(items, new Comparator<LookupElement>() {
      public int compare(LookupElement o1, LookupElement o2) {
        //noinspection unchecked
        return getSortingWeight(o1).compareTo(getSortingWeight(o2));
      }
    });
  }

  public void itemSelected(LookupElement item, final Lookup lookup) {
    final StatisticsManager manager = StatisticsManager.getInstance();
    manager.incUseCount(CompletionService.STATISTICS_KEY, item, myLocation);
    final List<LookupElement> items = lookup.getItems();
    final LookupImpl lookupImpl = (LookupImpl)lookup;
    final int count = Math.min(lookupImpl.getPreferredItemsCount(), lookupImpl.getList().getSelectedIndex());
    for (int i = 0; i < count; i++) {
      final LookupElement element = items.get(i);
      StatisticsInfo info = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, element, myLocation);
      if (info != null && info != StatisticsInfo.EMPTY && manager.getUseCount(info) == 0) {
        manager.incUseCount(new StatisticsInfo(composeContextWithValue(info), item == element ? SELECTED : IGNORED));
      }
    }

  }

  public int suggestPreselectedItem(List<LookupElement> sorted) {
    final CompletionPreselectSkipper[] skippers = CompletionPreselectSkipper.EP_NAME.getExtensions();

    nextItem: for (int i = 0; i < sorted.size(); i++){
      LookupElement item = sorted.get(i);
      final Object obj = item.getObject();
      if (obj instanceof PsiElement && !((PsiElement)obj).isValid()) continue;

      for (final CompletionPreselectSkipper skipper : skippers) {
        if (skipper.skipElement(item, myLocation)) {
          continue nextItem;
        }
      }

      return i;
    }
    return sorted.size() - 1;
  }

  public static String composeContextWithValue(final StatisticsInfo info) {
    return info.getContext() + "###" + info.getValue();
  }

  public Comparable[] getRelevanceWeight(final LookupElement item) {
    if (item.getUserData(WEIGHT) != null) return item.getUserData(WEIGHT);

    final Comparable[] result = new Comparable[]{WeighingService.weigh(CompletionService.RELEVANCE_KEY, item, myLocation)};

    item.putUserData(WEIGHT, result);

    return result;
  }

  public Comparable getSortingWeight(final LookupElement item) {
    final Comparable comparable = mySortingWeights.get(item);
    if (comparable != null) return comparable;

    final Comparable result = WeighingService.weigh(CompletionService.SORTING_KEY, item, myLocation);
    mySortingWeights.put(item, result);

    return result;
  }


  public static int doCompare(final double priority1, final double priority2, final Comparable[] weight1, final Comparable[] weight2) {
    if (priority1 != priority2) {
      final double v = priority1 - priority2;
      if (v > 0) return -1;
      if (v < 0) return 1;
    }

    for (int i = 0; i < weight1.length; i++) {
      final Comparable w1 = weight1[i];
      final Comparable w2 = i < weight2.length ? weight2[i]:null;
      if (w1 != null || w2 != null) {
        if (w1 == null) return 1;
        if (w2 == null) return -1;
        //noinspection unchecked
        final int res = w1.compareTo(w2);
        if (res != 0) return -res;
      }
    }

    return 0;
  }

  @Override
  public LookupItemWeightComparable getRelevance(LookupElement item) {
    LookupItemWeightComparable result = item.getUserData(RELEVANCE_KEY);
    if (result != null) return result;

    final double priority = item instanceof LookupItem ? ((LookupItem)item).getPriority() : 0;
    result = new LookupItemWeightComparable(priority, getRelevanceWeight(item));

    item.putUserData(RELEVANCE_KEY, result);

    return result;
  }

}
