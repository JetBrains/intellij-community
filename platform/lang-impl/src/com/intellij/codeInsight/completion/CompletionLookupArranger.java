/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.EqualityPolicy;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class CompletionLookupArranger extends LookupArranger {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CompletionLookupArranger");
  private static final Key<PresentationInvariant> PRESENTATION_INVARIANT = Key.create("PRESENTATION_INVARIANT");
  private final Comparator<LookupElement> BY_PRESENTATION_COMPARATOR = (o1, o2) -> {
    PresentationInvariant invariant = PRESENTATION_INVARIANT.get(o1);
    assert invariant != null;
    return invariant.compareTo(PRESENTATION_INVARIANT.get(o2));
  };
  static final int MAX_PREFERRED_COUNT = 5;
  public static final String OVERFLOW_MESSAGE = "Not all variants are shown, please type more letters to see the rest";
  public static final Key<WeighingContext> WEIGHING_CONTEXT = Key.create("WEIGHING_CONTEXT");
  public static final Key<Integer> PREFIX_CHANGES = Key.create("PREFIX_CHANGES");
  private static final UISettings ourUISettings = UISettings.getInstance();
  private final List<LookupElement> myFrozenItems = new ArrayList<>();
  private final int myLimit = Registry.intValue("ide.completion.variant.limit");
  private boolean myOverflow;

  @Nullable private CompletionLocation myLocation;
  private final CompletionProgressIndicator myProcess;
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final Map<CompletionSorterImpl, Classifier<LookupElement>> myClassifiers = new LinkedHashMap<>();
  private final Key<CompletionSorterImpl> mySorterKey = Key.create("SORTER_KEY");
  private final CompletionFinalSorter myFinalSorter = CompletionFinalSorter.newSorter();
  private int myPrefixChanges;

  CompletionLookupArranger(CompletionProgressIndicator process) {
    myProcess = process;
  }

  private MultiMap<CompletionSorterImpl, LookupElement> groupItemsBySorter(Iterable<LookupElement> source) {
    MultiMap<CompletionSorterImpl, LookupElement> inputBySorter = MultiMap.createLinked();
    for (LookupElement element : source) {
      inputBySorter.putValue(obtainSorter(element), element);
    }
    for (CompletionSorterImpl sorter : inputBySorter.keySet()) {
      inputBySorter.put(sorter, sortByPresentation(inputBySorter.get(sorter)));
    }

    return inputBySorter;
  }

  @NotNull
  private CompletionSorterImpl obtainSorter(LookupElement element) {
    //noinspection ConstantConditions
    return element.getUserData(mySorterKey);
  }

  @NotNull
  @Override
  public Map<LookupElement, List<Pair<String, Object>>> getRelevanceObjects(@NotNull Iterable<LookupElement> items,
                                                                               boolean hideSingleValued) {
    Map<LookupElement, List<Pair<String, Object>>> map = ContainerUtil.newIdentityHashMap();
    MultiMap<CompletionSorterImpl, LookupElement> inputBySorter = groupItemsBySorter(items);
    int sorterNumber = 0;
    for (CompletionSorterImpl sorter : inputBySorter.keySet()) {
      sorterNumber++;
      Collection<LookupElement> thisSorterItems = inputBySorter.get(sorter);
      for (LookupElement element : thisSorterItems) {
        map.put(element, ContainerUtil.newArrayList(new Pair<>("frozen", myFrozenItems.contains(element)),
                                                    new Pair<>("sorter", sorterNumber)));
      }
      ProcessingContext context = createContext();
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

    //noinspection unchecked
    Map<LookupElement, List<Pair<String, Object>>> result = new com.intellij.util.containers.hash.LinkedHashMap(EqualityPolicy.IDENTITY);
    Map<LookupElement, List<Pair<String, Object>>> additional = myFinalSorter.getRelevanceObjects(items);
    for (LookupElement item : items) {
      List<Pair<String, Object>> mainRelevance = map.get(item);
      List<Pair<String, Object>> additionalRelevance = additional.get(item);
      result.put(item, additionalRelevance == null ? mainRelevance : ContainerUtil.concat(mainRelevance, additionalRelevance));
    }
    return result;
  }

  void associateSorter(LookupElement element, CompletionSorterImpl sorter) {
    element.putUserData(mySorterKey, sorter);
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

    PresentationInvariant invariant = new PresentationInvariant(presentation.getItemText(), presentation.getTailText(), presentation.getTypeText());
    element.putUserData(PRESENTATION_INVARIANT, invariant);

    CompletionSorterImpl sorter = obtainSorter(element);
    Classifier<LookupElement> classifier = myClassifiers.get(sorter);
    if (classifier == null) {
      myClassifiers.put(sorter, classifier = sorter.buildClassifier(new EmptyClassifier()));
    }
    ProcessingContext context = createContext();
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
      myProcess.addAdvertisement(OVERFLOW_MESSAGE, null);

      // restart completion on any prefix change
      myProcess.addWatchedPrefix(0, StandardPatterns.string());

      if (ApplicationManager.getApplication().isUnitTestMode()) printTestWarning();
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private void printTestWarning() {
    System.err.println("Your test might miss some lookup items, because only " + (myLimit / 2) + " most relevant items are guaranteed to be shown in the lookup. You can:");
    System.err.println("1. Make the prefix used for completion longer, so that there are less suggestions.");
    System.err.println("2. Increase 'ide.completion.variant.limit' (using RegistryValue#setValue with a test root disposable).");
    System.err.println("3. Ignore this warning.");
  }

  private void removeItem(LookupElement element, ProcessingContext context) {
    CompletionSorterImpl sorter = obtainSorter(element);
    Classifier<LookupElement> classifier = myClassifiers.get(sorter);
    classifier.removeElement(element, context);
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
    return ourUISettings.getSortLookupElementsLexicographically();
  }

  @Override
  public Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction) {
    List<LookupElement> items = getMatchingItems();
    Iterable<LookupElement> sortedByRelevance = sortByRelevance(groupItemsBySorter(items));
    
    LookupElement relevantSelection = findMostRelevantItem(sortedByRelevance);
    LookupImpl lookupImpl = (LookupImpl)lookup;
    List<LookupElement> listModel = isAlphaSorted() ?
                                    sortByPresentation(items) :
                                    fillModelByRelevance(lookupImpl, ContainerUtil.newIdentityTroveSet(items), sortedByRelevance, relevantSelection);

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
                                                   Iterable<LookupElement> sortedElements,
                                                   @Nullable LookupElement relevantSelection) {
    Iterator<LookupElement> byRelevance = sortedElements.iterator();

    final LinkedHashSet<LookupElement> model = new LinkedHashSet<>();

    addPrefixItems(model);
    addFrozenItems(items, model);
    if (model.size() < MAX_PREFERRED_COUNT) {
      addSomeItems(model, byRelevance, lastAdded -> model.size() >= MAX_PREFERRED_COUNT);
    }
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
    final int limit = Math.max(list.getLastVisibleIndex(), model.size()) + ourUISettings.getMaxLookupListHeight() * 3;
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
    if (inputBySorter.isEmpty()) return Collections.emptyList();
    
    final List<Iterable<LookupElement>> byClassifier = ContainerUtil.newArrayList();
    for (CompletionSorterImpl sorter : myClassifiers.keySet()) {
      ProcessingContext context = createContext();
      byClassifier.add(myClassifiers.get(sorter).classify(inputBySorter.get(sorter), context));
    }
    //noinspection unchecked
    Iterable<LookupElement> result = ContainerUtil.concat(byClassifier.toArray(new Iterable[0]));
    return myFinalSorter.sort(result, Objects.requireNonNull(myProcess.getParameters()));
  }
  
  private ProcessingContext createContext() {
    ProcessingContext context = new ProcessingContext();
    context.put(PREFIX_CHANGES, myPrefixChanges);
    context.put(WEIGHING_CONTEXT, this);
    return context;
  }


  @Override
  public LookupArranger createEmptyCopy() {
    return new CompletionLookupArranger(myProcess);
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
        PresentationInvariant invariant = PRESENTATION_INVARIANT.get(items.get(i));
        if (invariant != null && invariant.equals(PRESENTATION_INVARIANT.get(lastSelection))) {
          return i;
        }
      }
    }

    LookupElement exactMatch = getBestExactMatch(lookup, items);
    return Math.max(0, ContainerUtil.indexOfIdentity(items, exactMatch != null ? exactMatch : mostRelevant));
  }

  private List<LookupElement> getExactMatches(LookupImpl lookup, List<LookupElement> items) {
    String selectedText = lookup.getTopLevelEditor().getSelectionModel().getSelectedText();
    List<LookupElement> exactMatches = new SmartList<>();
    for (int i = 0; i < items.size(); i++) {
      LookupElement item = items.get(i);
      boolean isSuddenLiveTemplate = isSuddenLiveTemplate(item);
      if (isPrefixItem(item, true) && !isSuddenLiveTemplate || item.getLookupString().equals(selectedText)) {
        if (item instanceof LiveTemplateLookupElement) {
          // prefer most recent live template lookup item
          return Collections.singletonList(item);
        }
        exactMatches.add(item);
      }
      else if (i == 0 && isSuddenLiveTemplate && items.size() > 1 && !CompletionServiceImpl.isStartMatch(items.get(1), this)) {
        return Collections.singletonList(item);
      }
    }
    return exactMatches;
  }

  @Nullable
  private LookupElement getBestExactMatch(LookupImpl lookup, List<LookupElement> items) {
    List<LookupElement> exactMatches = getExactMatches(lookup, items);
    if (exactMatches.isEmpty()) return null;

    if (exactMatches.size() == 1) {
      return exactMatches.get(0);
    }

    return sortByRelevance(groupItemsBySorter(exactMatches)).iterator().next();
  }

  @Nullable
  private LookupElement findMostRelevantItem(Iterable<LookupElement> sorted) {
    final CompletionPreselectSkipper[] skippers = CompletionPreselectSkipper.EP_NAME.getExtensions();
    
    for (LookupElement element : sorted) {
      if (!shouldSkip(skippers, element)) {
        return element;
      }
    }
    
    return null;
  }


  private static boolean isSuddenLiveTemplate(LookupElement element) {
    return element instanceof LiveTemplateLookupElement && ((LiveTemplateLookupElement)element).sudden;
  }

  private boolean shouldSkip(CompletionPreselectSkipper[] skippers, LookupElement element) {
    CompletionLocation location = myLocation;
    if (location == null) {
      location = new CompletionLocation(Objects.requireNonNull(myProcess.getParameters()));
    }
    for (final CompletionPreselectSkipper skipper : skippers) {
      if (skipper.skipElement(element, location)) {
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

  private static class EmptyClassifier extends Classifier<LookupElement> {

    private EmptyClassifier() {
      super(null, "empty");
    }

    @NotNull
    @Override
    public List<Pair<LookupElement, Object>> getSortingWeights(@NotNull Iterable<LookupElement> items, @NotNull ProcessingContext context) {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public Iterable<LookupElement> classify(@NotNull Iterable<LookupElement> source, @NotNull ProcessingContext context) {
      return source;
    }

  }
}
