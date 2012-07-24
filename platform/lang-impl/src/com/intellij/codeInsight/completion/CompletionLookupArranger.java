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

import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.FeatureUsageTrackerImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.Alarm;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CompletionLookupArranger extends LookupArranger {
  @Nullable private static StatisticsUpdate ourPendingUpdate;
  private static final Alarm ourStatsAlarm = new Alarm(ApplicationManager.getApplication());
  private static final Key<String> PRESENTATION_INVARIANT = Key.create("PRESENTATION_INVARIANT");
  private static final int MAX_PREFERRED_COUNT = 5;
  public static final Key<Boolean> PURE_RELEVANCE = Key.create("PURE_RELEVANCE");
  public static final Key<Integer> PREFIX_CHANGES = Key.create("PREFIX_CHANGES");
  private final List<LookupElement> myFrozenItems = new ArrayList<LookupElement>();
  static {
    Disposer.register(ApplicationManager.getApplication(), new Disposable() {
      @Override
      public void dispose() {
        cancelLastCompletionStatisticsUpdate();
      }
    });
  }

  private final CompletionLocation myLocation;
  private final CompletionParameters myParameters;
  private final CompletionProgressIndicator myProcess;
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final Map<CompletionSorterImpl, Classifier<LookupElement>> myClassifiers = new LinkedHashMap<CompletionSorterImpl, Classifier<LookupElement>>();
  private int myPrefixChanges;

  public CompletionLookupArranger(final CompletionParameters parameters, CompletionProgressIndicator process) {
    myParameters = parameters;
    myProcess = process;
    myLocation = new CompletionLocation(parameters);
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
  public Map<LookupElement, StringBuilder> getRelevanceStrings() {
    final LinkedHashMap<LookupElement,StringBuilder> map = new LinkedHashMap<LookupElement, StringBuilder>();
    for (LookupElement item : myItems) {
      map.put(item, new StringBuilder());
    }
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
      Classifier<LookupElement> classifier = myClassifiers.get(sorter);
      if (classifier != null) {
        classifier.describeItems(subMap, createContext(false));
      }
    }

    return map;

  }

  @Override
  public void addElement(Lookup lookup, LookupElement element, LookupElementPresentation presentation) {
    CompletionSorterImpl sorter = obtainSorter(element);
    Classifier<LookupElement> classifier = myClassifiers.get(sorter);
    if (classifier == null) {
      myClassifiers.put(sorter, classifier = sorter.buildClassifier());
    }
    classifier.addElement(element);

    final String invariant = presentation.getItemText() + "###" + presentation.getTailText() + "###" + presentation.getTypeText();
    element.putUserData(PRESENTATION_INVARIANT, invariant);
    super.addElement(lookup, element, presentation);
  }

  private static boolean isAlphaSorted() {
    return UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY;
  }

  @Override
  public Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction) {
    List<LookupElement> items = matchingItems(lookup);
    Collections.sort(items, new Comparator<LookupElement>() {
      public int compare(LookupElement o1, LookupElement o2) {
        String invariant = PRESENTATION_INVARIANT.get(o1);
        assert invariant != null;
        return invariant.compareToIgnoreCase(PRESENTATION_INVARIANT.get(o2));
      }
    });

    MultiMap<CompletionSorterImpl, LookupElement> inputBySorter = groupInputBySorter(items);

    final List<LookupElement> byRelevance = new ArrayList<LookupElement>();
    for (CompletionSorterImpl sorter : myClassifiers.keySet()) {
      ProcessingContext context = createContext(false);
      ContainerUtil.addAll(byRelevance, myClassifiers.get(sorter).classify(inputBySorter.get(sorter), context));
    }

    LinkedHashSet<LookupElement> model = new LinkedHashSet<LookupElement>();
    addPrefixItems(lookup, model, true, byRelevance);
    addPrefixItems(lookup, model, false, byRelevance);

    myFrozenItems.retainAll(items);
    model.addAll(myFrozenItems);

    if (!isAlphaSorted()) {
      for (int i = 0; i < byRelevance.size() && model.size() < MAX_PREFERRED_COUNT; i++) {
        model.add(byRelevance.get(i));
      }
      LookupElement lastSelection = lookup.getCurrentItem();
      if (items.contains(lastSelection)) {
        model.add(lastSelection);
      }
    }

    myFrozenItems.clear();

    if (((LookupImpl)lookup).isShown()) {
      myFrozenItems.addAll(model);
    }

    if (isAlphaSorted()) {
      model.addAll(items);
    } else  {
      model.addAll(byRelevance);
    }
    ArrayList<LookupElement> listModel = new ArrayList<LookupElement>(model);

    return new Pair<List<LookupElement>, Integer>(listModel, getItemToSelect(lookup, listModel, inputBySorter, onExplicitAction));
  }

  private ProcessingContext createContext(boolean pureRelevance) {
    ProcessingContext context = new ProcessingContext();
    context.put(PREFIX_CHANGES, myPrefixChanges);
    if (pureRelevance) {
      context.put(PURE_RELEVANCE, Boolean.TRUE);
    }
    return context;
  }


  @Override
  public LookupArranger createEmptyCopy() {
    return new CompletionLookupArranger(myParameters, myProcess);
  }

  private int getItemToSelect(Lookup lookup, List<LookupElement> items, MultiMap<CompletionSorterImpl, LookupElement> inputBySorter, boolean onExplicitAction) {
    if (items.isEmpty() || !lookup.isFocused()) {
      return 0;
    }

    if (lookup.isSelectionTouched() || !onExplicitAction) {
      LookupElement lastSelection = lookup.getCurrentItem();
      int old = items.indexOf(lastSelection);
      if (old >= 0) {
        return old;
      }

      for (int i = 0; i < items.size(); i++) {
        String invariant = PRESENTATION_INVARIANT.get(items.get(i));
        if (invariant != null && invariant.equals(PRESENTATION_INVARIANT.get(lastSelection))) {
          return i;
        }
      }
    }

    String selectedText = lookup.getEditor().getSelectionModel().getSelectedText();
    for (int i = 0; i < items.size(); i++) {
      LookupElement item = items.get(i);
      if (isPrefixItem(lookup, item, true) && !isLiveTemplate(item) ||
          item.getLookupString().equals(selectedText)) {
        return i;
      }
    }

    final CompletionPreselectSkipper[] skippers = CompletionPreselectSkipper.EP_NAME.getExtensions();
    for (CompletionSorterImpl sorter : myClassifiers.keySet()) {
      ProcessingContext context = createContext(true);
      for (LookupElement element : myClassifiers.get(sorter).classify(inputBySorter.get(sorter), context)) {
        if (!shouldSkip(skippers, element)) {
          return items.indexOf(element);
        }
      }
    }

    return items.size() - 1;
  }

  private static boolean isLiveTemplate(LookupElement element) {
    return element instanceof LiveTemplateLookupElement && ((LiveTemplateLookupElement)element).sudden;
  }

  public static StatisticsUpdate collectStatisticChanges(CompletionProgressIndicator indicator, LookupElement item) {
    applyLastCompletionStatisticsUpdate();

    CompletionLocation location = new CompletionLocation(indicator.getParameters());
    final StatisticsInfo main = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, item, location);

    final List<StatisticsInfo> toIncrement = new ArrayList<StatisticsInfo>();
    if (main != null && main != StatisticsInfo.EMPTY) {
      toIncrement.addAll(StatisticsWeigher.composeStatsWithPrefix(main, location, item));
    }

    StatisticsUpdate update = new StatisticsUpdate(toIncrement);
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

  @Override
  public void prefixChanged() {
    myPrefixChanges++;
    myFrozenItems.clear();
    super.prefixChanged();
  }

  static class StatisticsUpdate implements Disposable {
    private final List<StatisticsInfo> myInfos;
    private int mySpared;

    public StatisticsUpdate(List<StatisticsInfo> infos) {
      myInfos = infos;
    }

    void performUpdate() {
      for (StatisticsInfo statisticsInfo : myInfos) {
        StatisticsManager.getInstance().incUseCount(statisticsInfo);
      }
      ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getCompletionStatistics().registerInvocation(mySpared);
    }

    @Override
    public void dispose() {
    }

    public void addSparedChars(CompletionProgressIndicator indicator, LookupElement item, InsertionContext context, char completionChar) {
      String textInserted;
      if (context.getStartOffset() >= 0 && context.getTailOffset() >= context.getStartOffset()) {
        textInserted = context.getDocument().getText().substring(context.getStartOffset(), context.getTailOffset());
      } else {
        textInserted = item.getLookupString();
      }
      String withoutSpaces = StringUtil.replace(textInserted, new String[]{" ", "\t", "\n"}, new String[]{"", "", ""});
      int spared = withoutSpaces.length() - indicator.getLookup().itemPattern(item).length();
      if (completionChar != Lookup.NORMAL_SELECT_CHAR &&
          completionChar != Lookup.REPLACE_SELECT_CHAR &&
          completionChar != Lookup.AUTO_INSERT_SELECT_CHAR &&
          completionChar != Lookup.COMPLETE_STATEMENT_SELECT_CHAR &&
          withoutSpaces.contains(String.valueOf(completionChar))) {
        spared--;
      }
      if (spared > 0) {
        mySpared += spared;
      }
    }
  }
}
