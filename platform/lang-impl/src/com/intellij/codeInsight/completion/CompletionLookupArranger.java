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

import com.google.common.collect.Maps;
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupArranger;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.psi.WeighingService;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CompletionLookupArranger extends LookupArranger {
  private static final String SELECTED = "selected";
  static final String IGNORED = "ignored";
  private final CompletionLocation myLocation;
  private final Map<LookupElement, Comparable> mySortingWeights = new THashMap<LookupElement, Comparable>(TObjectHashingStrategy.IDENTITY);
  private final CompletionProgressIndicator myProcess;

  public CompletionLookupArranger(final CompletionParameters parameters, CompletionProgressIndicator process) {
    myProcess = process;
    myLocation = new CompletionLocation(parameters);
  }

  @Override
  @NotNull
  public Comparator<LookupElement> getItemComparator() {
    return new Comparator<LookupElement>() {
      public int compare(LookupElement o1, LookupElement o2) {
        //noinspection unchecked
        return mySortingWeights.get(o1).compareTo(mySortingWeights.get(o2));
      }
    };
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

  public int suggestPreselectedItem(List<LookupElement> sorted, Iterable<List<LookupElement>> groups) {
    final CompletionPreselectSkipper[] skippers = CompletionPreselectSkipper.EP_NAME.getExtensions();

    Set<LookupElement> model = new THashSet<LookupElement>(sorted);
    for (List<LookupElement> group : groups) {
      for (LookupElement element : group) {
        if (model.contains(element)) {
          for (final CompletionPreselectSkipper skipper : skippers) {
            if (!skipper.skipElement(element, myLocation)) {
              return sorted.indexOf(element);
            }
          }
        }
      }
    }

    return sorted.size() - 1;
  }

  public static String composeContextWithValue(final StatisticsInfo info) {
    return info.getContext() + "###" + info.getValue();
  }

  public Classifier<LookupElement> createRelevanceClassifier() {
    return new Classifier<LookupElement>() {
      @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
      private final FactoryMap<CompletionSorterImpl, Classifier<LookupElement>> myClassifiers = new FactoryMap<CompletionSorterImpl, Classifier<LookupElement>>() {
        @Override
        protected Map<CompletionSorterImpl, Classifier<LookupElement>> createMap() {
          return Maps.newLinkedHashMap();
        }

        @Override
        protected Classifier<LookupElement> create(CompletionSorterImpl key) {
          return key.buildClassifier();
        }
      };

      @Override
      public void addElement(LookupElement element) {
        mySortingWeights.put(element, WeighingService.weigh(CompletionService.SORTING_KEY, element, myLocation));
        myClassifiers.get(obtainSorter(element)).addElement(element);
      }

      @Override
      public Iterable<List<LookupElement>> classify(List<LookupElement> source) {
        MultiMap<CompletionSorterImpl, LookupElement> inputBySorter = groupInputBySorter(source);

        final ArrayList<List<LookupElement>> result = new ArrayList<List<LookupElement>>();
        for (CompletionSorterImpl sorter : myClassifiers.keySet()) {
          ContainerUtil.addAll(result, myClassifiers.get(sorter).classify((List<LookupElement>)inputBySorter.get(sorter)));
        }
        return result;
      }

      private MultiMap<CompletionSorterImpl, LookupElement> groupInputBySorter(List<LookupElement> source) {
        MultiMap<CompletionSorterImpl, LookupElement> inputBySorter = new MultiMap<CompletionSorterImpl, LookupElement>();
        for (LookupElement element : source) {
          inputBySorter.putValue(obtainSorter(element), element);
        }
        return inputBySorter;
      }

      @NotNull
      private CompletionSorterImpl obtainSorter(LookupElement element) {
        return myProcess.getSorter(element);
      }

      @Override
      public void describeItems(LinkedHashMap<LookupElement, StringBuilder> map) {
        final MultiMap<CompletionSorterImpl, LookupElement> inputBySorter = groupInputBySorter(new ArrayList<LookupElement>(map.keySet()));

        if (inputBySorter.size() > 1) {
          for (LookupElement element : map.keySet()) {
            map.get(element).append(obtainSorter(element)).append(": ");
          }
        }

        for (CompletionSorterImpl sorter : inputBySorter.keySet()) {
          final LinkedHashMap<LookupElement, StringBuilder> subMap = new LinkedHashMap<LookupElement, StringBuilder>();
          for (LookupElement element : inputBySorter.get(sorter)) {
            subMap.put(element, map.get(element));
          }
          myClassifiers.get(sorter).describeItems(subMap);
        }
      }
    };
  }

}
