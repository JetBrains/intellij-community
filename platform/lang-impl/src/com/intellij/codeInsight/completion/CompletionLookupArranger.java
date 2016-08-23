/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.util.Alarm;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class CompletionLookupArranger extends LookupArranger {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionLookupArranger");
  @Nullable private static StatisticsUpdate ourPendingUpdate;
  private static final Alarm ourStatsAlarm = new Alarm(ApplicationManager.getApplication());
  private static final Key<String> GLOBAL_PRESENTATION_INVARIANT = Key.create("PRESENTATION_INVARIANT");
  private final Key<String> PRESENTATION_INVARIANT = Key.create("PRESENTATION_INVARIANT");
  private final Comparator<LookupElement> BY_PRESENTATION_COMPARATOR = (o1, o2) -> {
    String invariant = PRESENTATION_INVARIANT.get(o1);
    assert invariant != null;
    return StringUtil.naturalCompare(invariant, PRESENTATION_INVARIANT.get(o2));
  };
  static final int MAX_PREFERRED_COUNT = 5;
  public static final Key<WeighingContext> WEIGHING_CONTEXT = Key.create("WEIGHING_CONTEXT");
  public static final Key<Boolean> PURE_RELEVANCE = Key.create("PURE_RELEVANCE");
  public static final Key<Integer> PREFIX_CHANGES = Key.create("PREFIX_CHANGES");
  private static final UISettings ourUISettings = UISettings.getInstance();
  private final List<LookupElement> myFrozenItems = new ArrayList<>();
  static {
    Disposer.register(ApplicationManager.getApplication(), new Disposable() {
      @Override
      public void dispose() {
        cancelLastCompletionStatisticsUpdate();
      }
    });
  }
  private final int myLimit = Registry.intValue("ide.completion.variant.limit");
  private boolean myOverflow;

  private final CompletionLocation myLocation;
  private final CompletionParameters myParameters;
  private final CompletionProgressIndicator myProcess;
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final Map<CompletionSorterImpl, Classifier<LookupElement>> myClassifiers = new LinkedHashMap<>();
  private int myPrefixChanges;

  public CompletionLookupArranger(final CompletionParameters parameters, CompletionProgressIndicator process) {
    myParameters = parameters;
    myProcess = process;
    myLocation = new CompletionLocation(parameters);
  }

  private MultiMap<CompletionSorterImpl, LookupElement> groupItemsBySorter(Iterable<LookupElement> source) {
    MultiMap<CompletionSorterImpl, LookupElement> inputBySorter = MultiMap.createLinked();
    for (LookupElement element : source) {
      inputBySorter.putValue(obtainSorter(element), element);
    }
    return inputBySorter;
  }

  @NotNull
  private CompletionSorterImpl obtainSorter(LookupElement element) {
    return myProcess.getSorter(element);
  }

  @NotNull
  @Override
  public Map<LookupElement, List<Pair<String, Object>>> getRelevanceObjects(@NotNull Iterable<LookupElement> items,
                                                                               boolean hideSingleValued) {
    final LinkedHashMap<LookupElement, List<Pair<String, Object>>> map = ContainerUtil.newLinkedHashMap();
    final MultiMap<CompletionSorterImpl, LookupElement> inputBySorter = groupItemsBySorter(items);
    int sorterNumber = 0;
    for (CompletionSorterImpl sorter : inputBySorter.keySet()) {
      sorterNumber++;
      Collection<LookupElement> thisSorterItems = inputBySorter.get(sorter);
      for (LookupElement element : thisSorterItems) {
        map.put(element, ContainerUtil.newArrayList(new Pair<>("frozen", myFrozenItems.contains(element)),
                                                    new Pair<>("sorter", sorterNumber)));
      }
      ProcessingContext context = createContext(false);
      Classifier<LookupElement> classifier = myClassifiers.get(sorter);
      while (classifier != null) {
        final THashSet<LookupElement> itemSet = ContainerUtil.newIdentityTroveSet(thisSorterItems);
        List<LookupElement> unsortedItems = ContainerUtil.filter(myItems, lookupElement -> itemSet.contains(lookupElement));
        List<Pair<LookupElement, Object>> pairs = classifier.getSortingWeights(unsortedItems, context);
        if (!hideSingleValued || !haveSameWeights(pairs)) {
          for (Pair<LookupElement, Object> pair : pairs) {
            map.get(pair.first).add(Pair.create(classifier.getPresentableName(), pair.second));
          }
        }
        classifier = classifier.getNext();
      }
    }

    return map;

  }

  private static boolean haveSameWeights(List<Pair<LookupElement, Object>> pairs) {
    if (pairs.isEmpty()) return true;

    for (int i = 1; i < pairs.size(); i++) {
      if (!Comparing.equal(pairs.get(i).second, pairs.get(0).second)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public void addElement(LookupElement element, LookupElementPresentation presentation) {
    StatisticsWeigher.clearBaseStatisticsInfo(element);

    final String invariant = presentation.getItemText() + "\0###" + getTailTextOrSpace(presentation) + "###" + presentation.getTypeText();
    element.putUserData(PRESENTATION_INVARIANT, invariant);
    element.putUserData(GLOBAL_PRESENTATION_INVARIANT, invariant);

    CompletionSorterImpl sorter = obtainSorter(element);
    Classifier<LookupElement> classifier = myClassifiers.get(sorter);
    if (classifier == null) {
      myClassifiers.put(sorter, classifier = sorter.buildClassifier(new AlphaClassifier()));
    }
    ProcessingContext context = createContext(true);
    classifier.addElement(element, context);

    super.addElement(element, presentation);

    trimToLimit(context);
  }

  @Override
  public void itemSelected(@Nullable LookupElement lookupItem, char completionChar) {
    myProcess.itemSelected(lookupItem, completionChar);
  }

  private void trimToLimit(ProcessingContext context) {
    if (myItems.size() < myLimit) return;

    List<LookupElement> items = getMatchingItems();
    Iterator<LookupElement> iterator = sortByRelevance(groupItemsBySorter(items)).iterator();

    final Set<LookupElement> retainedSet = ContainerUtil.newIdentityTroveSet();
    retainedSet.addAll(getPrefixItems(true));
    retainedSet.addAll(getPrefixItems(false));
    retainedSet.addAll(myFrozenItems);
    while (retainedSet.size() < myLimit / 2 && iterator.hasNext()) {
      retainedSet.add(iterator.next());
    }

    if (!iterator.hasNext()) return;

    List<LookupElement> removed = retainItems(retainedSet);
    for (LookupElement element : removed) {
      removeItem(element, context);
    }

    if (!myOverflow) {
      myOverflow = true;
      myProcess.addAdvertisement("Not all variants are shown, please type more letters to see the rest", null);

      // restart completion on any prefix change
      myProcess.addWatchedPrefix(0, StandardPatterns.string());
    }
  }

  private void removeItem(LookupElement element, ProcessingContext context) {
    CompletionSorterImpl sorter = obtainSorter(element);
    Classifier<LookupElement> classifier = myClassifiers.get(sorter);
    classifier.removeElement(element, context);
  }

  @NotNull
  private static String getTailTextOrSpace(LookupElementPresentation presentation) {
    String tailText = presentation.getTailText();
    return tailText == null || tailText.isEmpty() ? " " : tailText;
  }

  private List<LookupElement> sortByPresentation(Iterable<LookupElement> source) {
    ArrayList<LookupElement> startMatches = ContainerUtil.newArrayList();
    ArrayList<LookupElement> middleMatches = ContainerUtil.newArrayList();
    for (LookupElement element : source) {
      (itemMatcher(element).isStartMatch(element) ? startMatches : middleMatches).add(element);
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
    LookupImpl lookupImpl = (LookupImpl)lookup;
    List<LookupElement> listModel = isAlphaSorted() ?
                                    sortByPresentation(items) :
                                    fillModelByRelevance(lookupImpl, ContainerUtil.newIdentityTroveSet(items), itemsBySorter, relevantSelection);

    int toSelect = getItemToSelect(lookupImpl, listModel, onExplicitAction, relevantSelection);
    LOG.assertTrue(toSelect >= 0);

    addDummyItems(items.size() - listModel.size(), listModel);

    return new Pair<>(listModel, toSelect);
  }

  private static void addDummyItems(int count, List<LookupElement> listModel) {
    EmptyLookupItem dummy = new EmptyLookupItem("loading...", true);
    for (int i = count; i > 0; i--) {
      listModel.add(dummy);
    }
  }

  private List<LookupElement> fillModelByRelevance(LookupImpl lookup,
                                                   Set<LookupElement> items,
                                                   MultiMap<CompletionSorterImpl, LookupElement> inputBySorter,
                                                   @Nullable LookupElement relevantSelection) {
    Iterator<LookupElement> byRelevance = sortByRelevance(inputBySorter).iterator();

    final LinkedHashSet<LookupElement> model = new LinkedHashSet<>();

    addPrefixItems(model);
    addFrozenItems(items, model);
    addSomeItems(model, byRelevance, lastAdded -> model.size() >= MAX_PREFERRED_COUNT);
    addCurrentlySelectedItemToTop(lookup, items, model);

    freezeTopItems(lookup, model);

    ensureItemAdded(items, model, byRelevance, lookup.getCurrentItem());
    ensureItemAdded(items, model, byRelevance, relevantSelection);
    ensureEverythingVisibleAdded(lookup, model, byRelevance);

    return new ArrayList<>(model);
  }

  private static void ensureEverythingVisibleAdded(LookupImpl lookup, final LinkedHashSet<LookupElement> model, Iterator<LookupElement> byRelevance) {
    JList list = lookup.getList();
    final boolean testMode = ApplicationManager.getApplication().isUnitTestMode();
    final int limit = Math.max(list.getLastVisibleIndex(), model.size()) + ourUISettings.MAX_LOOKUP_LIST_HEIGHT * 3;
    addSomeItems(model, byRelevance, lastAdded -> !testMode && model.size() >= limit);
  }

  private static void ensureItemAdded(Set<LookupElement> items,
                                      LinkedHashSet<LookupElement> model,
                                      Iterator<LookupElement> byRelevance, @Nullable final LookupElement item) {
    if (item != null && items.contains(item) && !model.contains(item)) {
      addSomeItems(model, byRelevance, lastAdded -> lastAdded == item);
    }
  }

  private void freezeTopItems(LookupImpl lookup, LinkedHashSet<LookupElement> model) {
    myFrozenItems.clear();
    if (lookup.isShown()) {
      myFrozenItems.addAll(model);
    }
  }

  private void addFrozenItems(Set<LookupElement> items, LinkedHashSet<LookupElement> model) {
    for (Iterator<LookupElement> iterator = myFrozenItems.iterator(); iterator.hasNext(); ) {
      LookupElement element = iterator.next();
      if (!element.isValid() || !items.contains(element)) {
        iterator.remove();
      }
    }
    model.addAll(myFrozenItems);
  }

  private void addPrefixItems(LinkedHashSet<LookupElement> model) {
    ContainerUtil.addAll(model, sortByRelevance(groupItemsBySorter(getPrefixItems(true))));
    ContainerUtil.addAll(model, sortByRelevance(groupItemsBySorter(getPrefixItems(false))));
  }

  private static void addCurrentlySelectedItemToTop(Lookup lookup, Set<LookupElement> items, LinkedHashSet<LookupElement> model) {
    if (!lookup.isSelectionTouched()) {
      LookupElement lastSelection = lookup.getCurrentItem();
      if (items.contains(lastSelection)) {
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
    context.put(WEIGHING_CONTEXT, this);
    if (pureRelevance) {
      context.put(PURE_RELEVANCE, Boolean.TRUE);
    }
    return context;
  }


  @Override
  public LookupArranger createEmptyCopy() {
    return new CompletionLookupArranger(myParameters, myProcess);
  }

  private int getItemToSelect(LookupImpl lookup, List<LookupElement> items, boolean onExplicitAction, @Nullable LookupElement mostRelevant) {
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
        if (invariant != null && invariant.equals(GLOBAL_PRESENTATION_INVARIANT.get(lastSelection))) {
          return i;
        }
      }
    }

    String selectedText = lookup.getTopLevelEditor().getSelectionModel().getSelectedText();
    int exactMatchIndex = -1;
    for (int i = 0; i < items.size(); i++) {
      LookupElement item = items.get(i);
      boolean isSuddenLiveTemplate = isSuddenLiveTemplate(item);
      if (isPrefixItem(item, true) && !isSuddenLiveTemplate || item.getLookupString().equals(selectedText)) {
        if (item instanceof LiveTemplateLookupElement) {
          // prefer most recent live template lookup item
          exactMatchIndex = i;
          break;
        }
        if (exactMatchIndex == -1) {
          // prefer most recent item
          exactMatchIndex = i;
        }
      }
      else if (i == 0 && isSuddenLiveTemplate && items.size() > 1 && !CompletionServiceImpl.isStartMatch(items.get(1), this)) {
        return 0;
      }
    }
    if (exactMatchIndex >= 0) {
      return exactMatchIndex;
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


  private static boolean isSuddenLiveTemplate(LookupElement element) {
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

    if (!context.getOffsetMap().containsOffset(CompletionInitializationContext.START_OFFSET)) {
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

    ourStatsAlarm.addRequest(() -> {
      if (ourPendingUpdate == update) {
        applyLastCompletionStatisticsUpdate();
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
      if (context.getOffsetMap().containsOffset(CompletionInitializationContext.START_OFFSET) && 
          context.getOffsetMap().containsOffset(InsertionContext.TAIL_OFFSET) && 
          context.getTailOffset() >= context.getStartOffset()) {
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

  private class AlphaClassifier extends Classifier<LookupElement> {

    private AlphaClassifier() {
      super(null, "alpha");
    }

    @NotNull
    @Override
    public List<Pair<LookupElement, Object>> getSortingWeights(@NotNull Iterable<LookupElement> items, @NotNull ProcessingContext context) {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public Iterable<LookupElement> classify(@NotNull Iterable<LookupElement> source, @NotNull ProcessingContext context) {
      return sortByPresentation(source);
    }

  }
}
