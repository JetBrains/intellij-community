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

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.EmptyLookupItem;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.FeatureUsageTrackerImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.util.Alarm;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class CompletionLookupArranger extends LookupArranger {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionLookupArranger");
  @Nullable private static StatisticsUpdate ourPendingUpdate;
  private static final Alarm ourStatsAlarm = new Alarm(ApplicationManager.getApplication());
  private static final Key<String> PRESENTATION_INVARIANT = Key.create("PRESENTATION_INVARIANT");
  private static final Comparator<LookupElement> BY_PRESENTATION_COMPARATOR = new Comparator<LookupElement>() {
    @Override
    public int compare(LookupElement o1, LookupElement o2) {
      String invariant = PRESENTATION_INVARIANT.get(o1);
      assert invariant != null;
      return invariant.compareToIgnoreCase(PRESENTATION_INVARIANT.get(o2));
    }
  };
  private static final int MAX_PREFERRED_COUNT = 5;
  public static final Key<Boolean> PURE_RELEVANCE = Key.create("PURE_RELEVANCE");
  public static final Key<Integer> PREFIX_CHANGES = Key.create("PREFIX_CHANGES");
  private static final UISettings ourUISettings = UISettings.getInstance();
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

  private MultiMap<CompletionSorterImpl, LookupElement> groupItemsBySorter(List<LookupElement> source) {
    MultiMap<CompletionSorterImpl, LookupElement> inputBySorter = new MultiMap<CompletionSorterImpl, LookupElement>() {
      @Override
      protected Map<CompletionSorterImpl, Collection<LookupElement>> createMap() {
        return ContainerUtil.newLinkedHashMap();
      }
    };
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
    final MultiMap<CompletionSorterImpl, LookupElement> inputBySorter = groupItemsBySorter(new ArrayList<LookupElement>(map.keySet()));

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
    StatisticsWeigher.clearBaseStatisticsInfo(element);

    final String invariant = presentation.getItemText() + "\0###" + getTailTextOrSpace(presentation) + "###" + presentation.getTypeText();
    element.putUserData(PRESENTATION_INVARIANT, invariant);

    CompletionSorterImpl sorter = obtainSorter(element);
    Classifier<LookupElement> classifier = myClassifiers.get(sorter);
    if (classifier == null) {
      myClassifiers.put(sorter, classifier = sorter.buildClassifier(new AlphaClassifier(lookup)));
    }
    classifier.addElement(element);

    super.addElement(lookup, element, presentation);
  }

  @NotNull
  private static String getTailTextOrSpace(LookupElementPresentation presentation) {
    String tailText = presentation.getTailText();
    return tailText == null || tailText.isEmpty() ? " " : tailText;
  }

  private static List<LookupElement> sortByPresentation(Iterable<LookupElement> source, Lookup lookup) {
    ArrayList<LookupElement> startMatches = ContainerUtil.newArrayList();
    ArrayList<LookupElement> middleMatches = ContainerUtil.newArrayList();
    for (LookupElement element : source) {
      (CompletionServiceImpl.isStartMatch(element, lookup) ? startMatches : middleMatches).add(element);
    }
    ContainerUtil.sort(startMatches, BY_PRESENTATION_COMPARATOR);
    ContainerUtil.sort(middleMatches, BY_PRESENTATION_COMPARATOR);
    startMatches.addAll(middleMatches);
    return startMatches;
  }

  private static boolean isAlphaSorted() {
    return ourUISettings.SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY;
  }

  @Override
  public Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction) {
    List<LookupElement> items = getMatchingItems();
    MultiMap<CompletionSorterImpl, LookupElement> itemsBySorter = groupItemsBySorter(items);

    LookupElement relevantSelection = findMostRelevantItem(itemsBySorter);
    List<LookupElement> listModel = isAlphaSorted() ?
                                    sortByPresentation(items, lookup) :
                                    fillModelByRelevance((LookupImpl)lookup, items, itemsBySorter, relevantSelection);

    int toSelect = getItemToSelect((LookupImpl)lookup, listModel, onExplicitAction, relevantSelection);
    LOG.assertTrue(toSelect >= 0);

    addDummyItems(items.size() - listModel.size(), listModel);

    return new Pair<List<LookupElement>, Integer>(listModel, toSelect);
  }

  private static void addDummyItems(int count, List<LookupElement> listModel) {
    EmptyLookupItem dummy = new EmptyLookupItem("loading...", true);
    for (int i = count; i > 0; i--) {
      listModel.add(dummy);
    }
  }

  private List<LookupElement> fillModelByRelevance(LookupImpl lookup,
                                                   List<LookupElement> items,
                                                   MultiMap<CompletionSorterImpl, LookupElement> inputBySorter,
                                                   @Nullable LookupElement relevantSelection) {
    Iterator<LookupElement> byRelevance = sortByRelevance(inputBySorter).iterator();

    final LinkedHashSet<LookupElement> model = new LinkedHashSet<LookupElement>();

    addPrefixItems(model);
    addFrozenItems(items, model);
    addSomeItems(model, byRelevance, new Condition<LookupElement>() {
      @Override
      public boolean value(LookupElement lastAdded) {
        return model.size() >= MAX_PREFERRED_COUNT;
      }
    });
    addCurrentlySelectedItemToTop(lookup, items, model);

    freezeTopItems(lookup, model);

    ensureItemAdded(items, model, byRelevance, lookup.getCurrentItem());
    ensureItemAdded(items, model, byRelevance, relevantSelection);
    ensureEverythingVisibleAdded(lookup, model, byRelevance);

    return new ArrayList<LookupElement>(model);
  }

  private static void ensureEverythingVisibleAdded(LookupImpl lookup, final LinkedHashSet<LookupElement> model, Iterator<LookupElement> byRelevance) {
    JList list = lookup.getList();
    final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();
    final int limit = Math.max(list.getLastVisibleIndex(), model.size()) + ourUISettings.MAX_LOOKUP_LIST_HEIGHT * 3;
    addSomeItems(model, byRelevance, new Condition<LookupElement>() {
      @Override
      public boolean value(LookupElement lastAdded) {
        return !testMode && model.size() >= limit;
      }
    });
  }

  private static void ensureItemAdded(List<LookupElement> items,
                                      LinkedHashSet<LookupElement> model,
                                      Iterator<LookupElement> byRelevance, @Nullable final LookupElement item) {
    if (item != null && ContainerUtil.indexOfIdentity(items, item) >= 0 && !model.contains(item)) {
      addSomeItems(model, byRelevance, new Condition<LookupElement>() {
        @Override
        public boolean value(LookupElement lastAdded) {
          return lastAdded == item;
        }
      });
    }
  }

  private void freezeTopItems(LookupImpl lookup, LinkedHashSet<LookupElement> model) {
    myFrozenItems.clear();
    if (lookup.isShown()) {
      myFrozenItems.addAll(model);
    }
  }

  private void addFrozenItems(List<LookupElement> items, LinkedHashSet<LookupElement> model) {
    myFrozenItems.retainAll(items);
    model.addAll(myFrozenItems);
  }

  private void addPrefixItems(LinkedHashSet<LookupElement> model) {
    ContainerUtil.addAll(model, sortByRelevance(groupItemsBySorter(getPrefixItems(true))));
    ContainerUtil.addAll(model, sortByRelevance(groupItemsBySorter(getPrefixItems(false))));
  }

  private static void addCurrentlySelectedItemToTop(Lookup lookup, List<LookupElement> items, LinkedHashSet<LookupElement> model) {
    if (!lookup.isSelectionTouched()) {
      LookupElement lastSelection = lookup.getCurrentItem();
      if (ContainerUtil.indexOfIdentity(items, lastSelection) >= 0) {
        model.add(lastSelection);
      }
    }
  }

  private static void addSomeItems(LinkedHashSet<LookupElement> model, Iterator<LookupElement> iterator, Condition<LookupElement> stopWhen) {
    while (iterator.hasNext()) {
      LookupElement item = iterator.next();
      model.add(item);
      if (stopWhen.value(item)) {
        break;
      }
    }
  }

  private Iterable<LookupElement> sortByRelevance(MultiMap<CompletionSorterImpl, LookupElement> inputBySorter) {
    final List<Iterable<LookupElement>> byClassifier = ContainerUtil.newArrayList();
    for (CompletionSorterImpl sorter : myClassifiers.keySet()) {
      ProcessingContext context = createContext(false);
      byClassifier.add(myClassifiers.get(sorter).classify(inputBySorter.get(sorter), context));
    }
    //noinspection unchecked
    return ContainerUtil.concat(byClassifier.toArray(new Iterable[byClassifier.size()]));
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

  private static int getItemToSelect(LookupImpl lookup, List<LookupElement> items, boolean onExplicitAction, @Nullable LookupElement mostRelevant) {
    if (items.isEmpty() || lookup.getFocusDegree() == LookupImpl.FocusDegree.UNFOCUSED) {
      return 0;
    }

    if (lookup.isSelectionTouched() || !onExplicitAction) {
      final LookupElement lastSelection = lookup.getCurrentItem();
      int old = ContainerUtil.indexOfIdentity(items, lastSelection);
      if (old >= 0) {
        return old;
      }

      Object selectedValue = lookup.getList().getSelectedValue();
      if (selectedValue instanceof EmptyLookupItem && ((EmptyLookupItem)selectedValue).isLoading()) {
        int index = lookup.getList().getSelectedIndex();
        if (index >= 0 && index < items.size()) {
          return index;
        }
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
      boolean isTemplate = isLiveTemplate(item);
      if (isPrefixItem(lookup, item, true) && !isTemplate ||
          item.getLookupString().equals(selectedText)) {
        return i;
      }
      if (i == 0 && isTemplate && items.size() > 1 && !CompletionServiceImpl.isStartMatch(items.get(1), lookup)) {
        return 0;
      }
    }

    return Math.max(0, ContainerUtil.indexOfIdentity(items, mostRelevant));
  }

  @Nullable
  private LookupElement findMostRelevantItem(MultiMap<CompletionSorterImpl, LookupElement> itemsBySorter) {
    final CompletionPreselectSkipper[] skippers = CompletionPreselectSkipper.EP_NAME.getExtensions();
    for (CompletionSorterImpl sorter : myClassifiers.keySet()) {
      ProcessingContext context = createContext(true);
      for (LookupElement element : myClassifiers.get(sorter).classify(itemsBySorter.get(sorter), context)) {
        if (!shouldSkip(skippers, element)) {
          return element;
        }
      }
    }

    return null;
  }


  private static boolean isLiveTemplate(LookupElement element) {
    return element instanceof LiveTemplateLookupElement && ((LiveTemplateLookupElement)element).sudden;
  }

  public static StatisticsUpdate collectStatisticChanges(LookupElement item, final Lookup lookup) {
    applyLastCompletionStatisticsUpdate();

    final StatisticsInfo base = StatisticsWeigher.getBaseStatisticsInfo(item, null);
    if (base == StatisticsInfo.EMPTY) {
      return new StatisticsUpdate(StatisticsInfo.EMPTY);
    }

    StatisticsUpdate update = new StatisticsUpdate(StatisticsWeigher.composeStatsWithPrefix(base, lookup.itemPattern(item), true));
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
        if (LOG.isDebugEnabled()) {
          LOG.debug("Skipped element " + element + " by " + skipper);
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public void prefixChanged(Lookup lookup) {
    myPrefixChanges++;
    myFrozenItems.clear();
    super.prefixChanged(lookup);
  }

  static class StatisticsUpdate implements Disposable {
    private final StatisticsInfo myInfo;
    private int mySpared;

    public StatisticsUpdate(StatisticsInfo info) {
      myInfo = info;
    }

    void performUpdate() {
      myInfo.incUseCount();
      ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getCompletionStatistics().registerInvocation(mySpared);
    }

    @Override
    public void dispose() {
    }

    public void addSparedChars(CompletionProgressIndicator indicator, LookupElement item, InsertionContext context, char completionChar) {
      String textInserted;
      if (context.getStartOffset() >= 0 && context.getTailOffset() >= context.getStartOffset()) {
        textInserted = context.getDocument().getImmutableCharSequence().subSequence(context.getStartOffset(), context.getTailOffset()).toString();
      } else {
        textInserted = item.getLookupString();
      }
      String withoutSpaces = StringUtil.replace(textInserted, new String[]{" ", "\t", "\n"}, new String[]{"", "", ""});
      int spared = withoutSpaces.length() - indicator.getLookup().itemPattern(item).length();
      if (!LookupEvent.isSpecialCompletionChar(completionChar) && withoutSpaces.contains(String.valueOf(completionChar))) {
        spared--;
      }
      if (spared > 0) {
        mySpared += spared;
      }
    }
  }

  private static class AlphaClassifier extends Classifier<LookupElement> {
    private final Lookup myLookup;

    private AlphaClassifier(Lookup lookup) {
      myLookup = lookup;
    }

    @Override
    public void addElement(LookupElement element) {
    }

    @Override
    public Iterable<LookupElement> classify(Iterable<LookupElement> source, ProcessingContext context) {
      return sortByPresentation(source, myLookup);
    }

    @Override
    public void describeItems(LinkedHashMap<LookupElement, StringBuilder> map, ProcessingContext context) {
    }
  }
}
