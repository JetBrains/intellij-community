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
import com.intellij.codeInsight.lookup.LookupArranger;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.WeighingService;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CompletionLookupArranger extends LookupArranger {
  @Nullable private static StatisticsUpdate ourPendingUpdate;
  private static final Alarm ourStatsAlarm = new Alarm(ApplicationManager.getApplication());
  
  static {
    Disposer.register(ApplicationManager.getApplication(), new Disposable() {
      @Override
      public void dispose() {
        cancelLastCompletionStatisticsUpdate();
      }
    });
  }

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

  public static StatisticsUpdate collectStatisticChanges(CompletionProgressIndicator indicator, LookupElement item) {
    LookupImpl lookupImpl = indicator.getLookup();
    applyLastCompletionStatisticsUpdate();

    CompletionLocation myLocation = new CompletionLocation(indicator.getParameters());
    final StatisticsInfo main = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, item, myLocation);
    final List<LookupElement> items = lookupImpl.getItems();
    final int count = Math.min(lookupImpl.getPreferredItemsCount(), lookupImpl.getList().getSelectedIndex());

    final List<StatisticsInfo> ignored = new ArrayList<StatisticsInfo>();
    for (int i = 0; i < count; i++) {
      final LookupElement element = items.get(i);
      StatisticsInfo baseInfo = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, element, myLocation);
      if (baseInfo != null && baseInfo != StatisticsInfo.EMPTY && StatisticsManager.getInstance().getUseCount(baseInfo) == 0) {
        ignored.add(new StatisticsInfo(composeContextWithValue(baseInfo), IGNORED));
      }
    }

    StatisticsInfo info = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, item, myLocation);
    final StatisticsInfo selected =
      info != null && info != StatisticsInfo.EMPTY ? new StatisticsInfo(composeContextWithValue(info), SELECTED) : null;

    StatisticsUpdate update = new StatisticsUpdate(ignored, selected, main);
    ourPendingUpdate = update;
    Disposer.register(update, new Disposable() {
      @Override
      public void dispose() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourPendingUpdate = null;
      }
    });

    return update;
  }

  public static void trackStatistics(InsertionContext context, final StatisticsUpdate update) {
    if (ourPendingUpdate != update) {
      return;
    }

    final Document document = context.getDocument();
    int startOffset = context.getStartOffset();
    int tailOffset = context.getEditor().getCaretModel().getOffset();
    if (startOffset < 0 || tailOffset <= startOffset) {
      return;
    }

    final RangeMarker marker = document.createRangeMarker(startOffset, tailOffset);
    final DocumentAdapter listener = new DocumentAdapter() {
      @Override
      public void beforeDocumentChange(DocumentEvent e) {
        if (!marker.isValid() || e.getOffset() > marker.getStartOffset() && e.getOffset() < marker.getEndOffset()) {
          cancelLastCompletionStatisticsUpdate();
        }
      }
    };

    ourStatsAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (ourPendingUpdate == update) {
          applyLastCompletionStatisticsUpdate();
        }
      }
    }, 20 * 1000);

    document.addDocumentListener(listener);
    Disposer.register(update, new Disposable() {
      @Override
      public void dispose() {
        document.removeDocumentListener(listener);
        marker.dispose();
        ourStatsAlarm.cancelAllRequests();
      }
    });
  }

  public static void cancelLastCompletionStatisticsUpdate() {
    if (ourPendingUpdate != null) {
      Disposer.dispose(ourPendingUpdate);
      assert ourPendingUpdate == null;
    }
  }

  public static void applyLastCompletionStatisticsUpdate() {
    StatisticsUpdate update = ourPendingUpdate;
    if (update != null) {
      update.performUpdate();
      Disposer.dispose(update);
      assert ourPendingUpdate == null;
    }
  }

  public int suggestPreselectedItem(List<LookupElement> sorted, Iterable<List<LookupElement>> groups) {
    final CompletionPreselectSkipper[] skippers = CompletionPreselectSkipper.EP_NAME.getExtensions();

    Set<LookupElement> model = new THashSet<LookupElement>(sorted);
    for (List<LookupElement> group : groups) {
      for (LookupElement element : group) {
        if (model.contains(element)) {
          if (!shouldSkip(skippers, element)) {
            return sorted.indexOf(element);
          }
        }
      }
    }

    return sorted.size() - 1;
  }

  private boolean shouldSkip(CompletionPreselectSkipper[] skippers, LookupElement element) {
    for (final CompletionPreselectSkipper skipper : skippers) {
      if (skipper.skipElement(element, myLocation)) {
        return true;
      }
    }
    return false;
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

  static class StatisticsUpdate implements Disposable {
    private final List<StatisticsInfo> myIgnored;
    private final StatisticsInfo mySelected;
    private final StatisticsInfo myMain;

    public StatisticsUpdate(List<StatisticsInfo> ignored, StatisticsInfo selected, StatisticsInfo main) {
      myIgnored = ignored;
      mySelected = selected;
      myMain = main;
    }

    void performUpdate() {
      for (StatisticsInfo statisticsInfo : myIgnored) {
        StatisticsManager.getInstance().incUseCount(statisticsInfo);
      }
      if (mySelected != null) {
        StatisticsManager.getInstance().incUseCount(mySelected);
      }
      if (myMain != null) {
        StatisticsManager.getInstance().incUseCount(myMain);
      }
    }

    @Override
    public void dispose() {
    }
  }
}
