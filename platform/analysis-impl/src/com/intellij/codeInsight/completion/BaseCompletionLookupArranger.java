// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.EmptyLookupItem;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.StandardPatterns;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class BaseCompletionLookupArranger extends LookupArranger implements CompletionLookupArranger {
  private static final Logger LOG = Logger.getInstance(BaseCompletionLookupArranger.class);
  private static final Key<LookupElementPresentation> DEFAULT_PRESENTATION = Key.create("PRESENTATION_INVARIANT");
  private static final Comparator<LookupElementPresentation> PRESENTATION_COMPARATOR = Comparator
    .comparing(LookupElementPresentation::getItemText, NaturalComparator.INSTANCE)
    .thenComparing(p -> StringUtil.notNullize(p.getTailText()).length())
    .thenComparing(LookupElementPresentation::getTailText, NaturalComparator.INSTANCE)
    .thenComparing(LookupElementPresentation::getTypeText, NaturalComparator.INSTANCE);
  private static final Comparator<LookupElement> BY_PRESENTATION_COMPARATOR =
    Comparator.comparing(DEFAULT_PRESENTATION::get, PRESENTATION_COMPARATOR);
  static final int MAX_PREFERRED_COUNT = 5;
  public static final Key<Object> FORCE_MIDDLE_MATCH = Key.create("FORCE_MIDDLE_MATCH");

  private final List<LookupElement> myFrozenItems = new ArrayList<>();
  private final int myLimit = Registry.intValue("ide.completion.variant.limit");
  private boolean myOverflow;

  private volatile CompletionLocation myLocation;
  protected final CompletionProcessEx myProcess;
  private final Map<CompletionSorterImpl, Classifier<LookupElement>> myClassifiers = new LinkedHashMap<>();
  private final Key<CompletionSorterImpl> mySorterKey = Key.create("SORTER_KEY");
  private final CompletionFinalSorter myFinalSorter = CompletionFinalSorter.newSorter();
  private int myPrefixChanges;

  private String myLastLookupPrefix;

  private final CompletionPreselectSkipper[] mySkippers = CompletionPreselectSkipper.EP_NAME.getExtensions();
  private final Set<LookupElement> mySkippedItems = Collections.newSetFromMap(new IdentityHashMap<>());

  public BaseCompletionLookupArranger(CompletionProcessEx process) {
    myProcess = process;
  }

  private MultiMap<CompletionSorterImpl, LookupElement> groupItemsBySorter(Iterable<? extends LookupElement> source) {
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
  public synchronized Map<LookupElement, List<Pair<String, Object>>> getRelevanceObjects(@NotNull Iterable<? extends LookupElement> items,
                                                                            boolean hideSingleValued) {
    Map<LookupElement, List<Pair<String, Object>>> map = new IdentityHashMap<>();
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
        Set<LookupElement> itemSet = new ReferenceOpenHashSet<>(thisSorterItems);
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

    Map<LookupElement, List<Pair<String, Object>>> result = new Reference2ObjectLinkedOpenHashMap<>();
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

  public void clearClassifierCache() {
    myClassifiers.clear();
  }

  private static boolean haveSameWeights(List<? extends Pair<LookupElement, Object>> pairs) {
    if (pairs.isEmpty()) return true;

    for (int i = 1; i < pairs.size(); i++) {
      if (!Comparing.equal(pairs.get(i).second, pairs.get(0).second)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public void addElement(@NotNull LookupElement element,
                         @NotNull CompletionSorter sorter,
                         @NotNull PrefixMatcher prefixMatcher,
                         @NotNull LookupElementPresentation presentation) {
    registerMatcher(element, prefixMatcher);
    associateSorter(element, (CompletionSorterImpl)sorter);
    addElement(element, presentation);
  }

  @Override
  public void addElement(@NotNull CompletionResult result) {
    LookupElementPresentation presentation = new LookupElementPresentation();
    result.getLookupElement().renderElement(presentation);
    addElement(result.getLookupElement(), result.getSorter(), result.getPrefixMatcher(), presentation);
  }

  @Override
  public void addElement(LookupElement element, LookupElementPresentation presentation) {
    boolean shouldSkip = shouldSkip(element);
    presentation.freeze();
    element.putUserData(DEFAULT_PRESENTATION, presentation);
    CompletionSorterImpl sorter = obtainSorter(element);
    ProcessingContext context = createContext();
    synchronized (this) {
      Classifier<LookupElement> classifier = myClassifiers.computeIfAbsent(sorter, s -> s.buildClassifier(new EmptyClassifier()));
      classifier.addElement(element, context);

      if (shouldSkip) {
        mySkippedItems.add(element);
      }

      if (Boolean.TRUE.equals(isInBatchUpdate.get())) {
        batchItems.add(new Pair<>(element, presentation));
      } else {
        super.addElement(element, presentation);
        trimToLimit(context);
      }
    }
  }

  private final ThreadLocal<Boolean> isInBatchUpdate = new ThreadLocal<>();
  private final List<Pair<LookupElement, LookupElementPresentation>> batchItems = new ArrayList<>();

  @ApiStatus.Internal
  public void batchUpdate(Runnable runnable) {
    if (Boolean.TRUE.equals(isInBatchUpdate.get())) {
      runnable.run();
    } else {
      isInBatchUpdate.set(true);
      try {
        runnable.run();
      } finally {
        isInBatchUpdate.remove();
      }
      if (!batchItems.isEmpty()) {
        flushBatch();
      }
    }
  }

  private synchronized void flushBatch() {
    for (Pair<LookupElement, LookupElementPresentation> pair: batchItems) {
      super.addElement(pair.first, pair.second);
    }
    batchItems.clear();
    trimToLimit(createContext());
  }

  @Override
  public synchronized void prefixReplaced(@NotNull Lookup lookup, @NotNull String newPrefix) {
    super.prefixReplaced(lookup, newPrefix);
  }

  @Override
  public void itemSelected(@Nullable LookupElement lookupItem, char completionChar) {
    myProcess.itemSelected(lookupItem, completionChar);
  }

  private void trimToLimit(ProcessingContext context) {
    if (myItems.size() < myLimit) return;

    List<LookupElement> items = getMatchingItems();
    Iterator<LookupElement> iterator = sortByRelevance(groupItemsBySorter(items)).iterator();

    Set<LookupElement> retainedSet = new ReferenceOpenHashSet<>();
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
      myProcess.addAdvertisement(AnalysisBundle.message("completion.not.all.variants.are.shown"), null);

      // restart completion on any prefix change
      myProcess.addWatchedPrefix(0, StandardPatterns.string());

      if (ApplicationManager.getApplication().isUnitTestMode()) printTestWarning();
    }
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "HardCodedStringLiteral"})
  private void printTestWarning() {
    System.err.println("Your test might miss some lookup items, because only " + (myLimit / 2) + " most relevant items are guaranteed to be shown in the lookup. You can:");
    System.err.println("1. Make the prefix used for completion longer, so that there are less suggestions.");
    System.err.println("2. Increase 'ide.completion.variant.limit' (using RegistryValue#setValue with a test root disposable).");
    System.err.println("3. Ignore this warning.");
  }

  protected void removeItem(LookupElement element, ProcessingContext context) {
    CompletionSorterImpl sorter = obtainSorter(element);
    Classifier<LookupElement> classifier = myClassifiers.get(sorter);
    if (classifier != null)
      classifier.removeElement(element, context);
  }

  private List<LookupElement> sortByPresentation(Iterable<? extends LookupElement> source) {
    List<LookupElement> startMatches = new ArrayList<>();
    List<LookupElement> middleMatches = new ArrayList<>();
    for (LookupElement element : source) {
      (itemMatcher(element).isStartMatch(element) ? startMatches : middleMatches).add(element);
    }
    ContainerUtil.sort(startMatches, BY_PRESENTATION_COMPARATOR);
    ContainerUtil.sort(middleMatches, BY_PRESENTATION_COMPARATOR);
    startMatches.addAll(middleMatches);
    return startMatches;
  }

  protected boolean isAlphaSorted() {
    return false;
  }

  @Override
  public Pair<List<LookupElement>, Integer> arrangeItems() {
    LookupElementListPresenter dummyListPresenter = new LookupElementListPresenter() {
      @Override
      public String getAdditionalPrefix() {
        return "";
      }

      @Override
      public LookupElement getCurrentItem() {
        return null;
      }

      @Override
      public LookupElement getCurrentItemOrEmpty() {
        return null;
      }

      @Override
      public boolean isSelectionTouched() {
        return false;
      }

      @Override
      public int getSelectedIndex() {
        return 0;
      }

      @Override
      public int getLastVisibleIndex() {
        return 0;
      }

      @NotNull
      @Override
      public LookupFocusDegree getLookupFocusDegree() {
        return LookupFocusDegree.FOCUSED;
      }

      @Override
      public boolean isShown() {
        return true;
      }
    };
    return doArrangeItems(dummyListPresenter, false);
  }

  @Override
  public Pair<List<LookupElement>, Integer> arrangeItems(@NotNull Lookup lookup, boolean onExplicitAction) {
    return doArrangeItems((LookupElementListPresenter)lookup, onExplicitAction);
  }

  @NotNull
  private synchronized Pair<List<LookupElement>, Integer> doArrangeItems(@NotNull LookupElementListPresenter lookup, boolean onExplicitAction) {
    List<LookupElement> items = getMatchingItems();
    Iterable<? extends LookupElement> sortedByRelevance = sortByRelevance(groupItemsBySorter(items));

    sortedByRelevance = applyFinalSorter(sortedByRelevance);

    LookupElement relevantSelection = findMostRelevantItem(sortedByRelevance);
    List<LookupElement> listModel = isAlphaSorted() ?
                                    sortByPresentation(items) :
                                    fillModelByRelevance(lookup, new ReferenceOpenHashSet<>(items), sortedByRelevance, relevantSelection);

    int toSelect = getItemToSelect(lookup, listModel, onExplicitAction, relevantSelection);
    LOG.assertTrue(toSelect >= 0);

    return new Pair<>(listModel, toSelect);
  }

  // visible for plugins, see https://intellij-support.jetbrains.com/hc/en-us/community/posts/360008625980-Sorting-completions-in-provider
  @NotNull
  protected Iterable<? extends LookupElement> applyFinalSorter(Iterable<? extends LookupElement> sortedByRelevance) {
    if (sortedByRelevance.iterator().hasNext()) {
      return myFinalSorter.sort(sortedByRelevance, Objects.requireNonNull(myProcess.getParameters()));
    }
    return sortedByRelevance;
  }

  private List<LookupElement> fillModelByRelevance(LookupElementListPresenter lookup,
                                                   Set<? extends LookupElement> items,
                                                   Iterable<? extends LookupElement> sortedElements,
                                                   @Nullable LookupElement relevantSelection) {
    Iterator<? extends LookupElement> byRelevance = sortedElements.iterator();

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
    ContainerUtil.addAll(model, byRelevance);

    return new ArrayList<>(model);
  }

  private static void ensureItemAdded(Set<? extends LookupElement> items,
                                      LinkedHashSet<? super LookupElement> model,
                                      Iterator<? extends LookupElement> byRelevance, @Nullable final LookupElement item) {
    if (item != null && items.contains(item) && !model.contains(item)) {
      addSomeItems(model, byRelevance, lastAdded -> lastAdded == item);
    }
  }

  private void freezeTopItems(LookupElementListPresenter lookup, LinkedHashSet<? extends LookupElement> model) {
    myFrozenItems.clear();
    if (lookup.isShown()) {
      myFrozenItems.addAll(model);
    }
  }

  private void addFrozenItems(Set<? extends LookupElement> items, LinkedHashSet<? super LookupElement> model) {
    for (Iterator<LookupElement> iterator = myFrozenItems.iterator(); iterator.hasNext(); ) {
      LookupElement element = iterator.next();
      if (!element.isValid() || !items.contains(element)) {
        iterator.remove();
      }
    }
    model.addAll(myFrozenItems);
  }

  private void addPrefixItems(LinkedHashSet<? super LookupElement> model) {
    ContainerUtil.addAll(model, sortByRelevance(groupItemsBySorter(getPrefixItems(true))));
    ContainerUtil.addAll(model, sortByRelevance(groupItemsBySorter(getPrefixItems(false))));
  }

  private static void addCurrentlySelectedItemToTop(LookupElementListPresenter lookup, Set<? extends LookupElement> items, LinkedHashSet<? super LookupElement> model) {
    if (!lookup.isSelectionTouched()) {
      LookupElement lastSelection = lookup.getCurrentItem();
      if (items.contains(lastSelection)) {
        model.add(lastSelection);
      }
    }
  }

  private static void addSomeItems(Set<? super LookupElement> model, Iterator<? extends LookupElement> iterator, Predicate<? super LookupElement> stopWhen) {
    while (iterator.hasNext()) {
      LookupElement item = iterator.next();
      model.add(item);
      if (stopWhen.test(item)) {
        break;
      }
    }
  }

  private Iterable<LookupElement> sortByRelevance(MultiMap<CompletionSorterImpl, LookupElement> inputBySorter) {
    if (inputBySorter.isEmpty()) return Collections.emptyList();

    final List<Iterable<LookupElement>> byClassifier = new ArrayList<>();
    for (CompletionSorterImpl sorter : myClassifiers.keySet()) {
      ProcessingContext context = createContext();
      byClassifier.add(myClassifiers.get(sorter).classify(inputBySorter.get(sorter), context));
    }
    //noinspection unchecked
    return ContainerUtil.concat(byClassifier.toArray(new Iterable[0]));
  }

  private ProcessingContext createContext() {
    ProcessingContext context = new ProcessingContext();
    context.put(PREFIX_CHANGES, myPrefixChanges);
    context.put(WEIGHING_CONTEXT, this);
    return context;
  }

  void setLastLookupPrefix(String lookupPrefix) {
    myLastLookupPrefix = lookupPrefix;
  }

  public String getLastLookupPrefix() {
    return myLastLookupPrefix;
  }

  @Override
  public LookupArranger createEmptyCopy() {
    return new BaseCompletionLookupArranger(myProcess);
  }

  private int getItemToSelect(LookupElementListPresenter lookup, List<? extends LookupElement> items, boolean onExplicitAction, @Nullable LookupElement mostRelevant) {
    if (items.isEmpty() || lookup.getLookupFocusDegree() == LookupFocusDegree.UNFOCUSED) {
      return 0;
    }

    if (lookup.isSelectionTouched() || !onExplicitAction) {
      final LookupElement lastSelection = lookup.getCurrentItem();
      int old = ContainerUtil.indexOfIdentity(items, lastSelection);
      if (old >= 0) {
        return old;
      }

      LookupElement selectedValue = lookup.getCurrentItemOrEmpty();
      if (selectedValue instanceof EmptyLookupItem && ((EmptyLookupItem)selectedValue).isLoading()) {
        int index = lookup.getSelectedIndex();
        if (index >= 0 && index < items.size()) {
          return index;
        }
      }

      for (int i = 0; i < items.size(); i++) {
        LookupElementPresentation p1 = getDefaultPresentation(items.get(i));
        LookupElementPresentation p2 = lastSelection == null ? null : getDefaultPresentation(lastSelection);
        if (p1 != null && p2 != null && PRESENTATION_COMPARATOR.compare(p1, p2) == 0) {
          return i;
        }
      }
    }

    LookupElement exactMatch = getBestExactMatch(items);
    return Math.max(0, ContainerUtil.indexOfIdentity(items, exactMatch != null ? exactMatch : mostRelevant));
  }

  /**
   * @return the presentation returned by {@link LookupElement#renderElement} at the moment of this item's addition to the lookup.
   */
  @ApiStatus.Internal
  public static LookupElementPresentation getDefaultPresentation(@NotNull LookupElement item) {
    return item.getUserData(DEFAULT_PRESENTATION);
  }

  protected List<LookupElement> getExactMatches(List<? extends LookupElement> items) {
    Editor editor = myProcess.getParameters().getEditor();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
    }
    String selectedText = editor.getSelectionModel().getSelectedText();
    List<LookupElement> exactMatches = new SmartList<>();
    for (LookupElement item : items) {
      if (isPrefixItem(item, true) || item.getLookupString().equals(selectedText)) {
        exactMatches.add(item);
      }
    }
    return exactMatches;
  }

  @Nullable
  private LookupElement getBestExactMatch(List<? extends LookupElement> items) {
    List<LookupElement> exactMatches = getExactMatches(items);
    if (exactMatches.isEmpty()) return null;

    if (exactMatches.size() == 1) {
      return exactMatches.get(0);
    }

    return sortByRelevance(groupItemsBySorter(exactMatches)).iterator().next();
  }

  @Nullable
  private LookupElement findMostRelevantItem(Iterable<? extends LookupElement> sorted) {
    for (LookupElement element : sorted) {
      if (!mySkippedItems.contains(element)) {
        return element;
      }
    }

    return null;
  }

  private boolean shouldSkip(LookupElement element) {
    CompletionLocation location = getLocation();
    for (CompletionPreselectSkipper skipper : mySkippers) {
      if (skipper.skipElement(element, location)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Skipped element " + element + " by " + skipper);
        }
        return true;
      }
    }
    return false;
  }

  @NotNull
  private CompletionLocation getLocation() {
    if (myLocation == null) {
      synchronized (this) {
        if (myLocation == null) {
          myLocation = new CompletionLocation(Objects.requireNonNull(myProcess.getParameters()));
        }
      }
    }
    return myLocation;
  }

  @Override
  public synchronized void prefixChanged(Lookup lookup) {
    myPrefixChanges++;
    myFrozenItems.clear();
    super.prefixChanged(lookup);
  }

  @Override
  public void prefixTruncated(@NotNull LookupEx lookup, int hideOffset) {
    if (hideOffset < lookup.getEditor().getCaretModel().getOffset()) {
      myProcess.scheduleRestart();
      return;
    }
    myProcess.prefixUpdated();
    lookup.hideLookup(false);
  }

  @Override
  public boolean isCompletion() {
    return true;
  }

  private static final class EmptyClassifier extends Classifier<LookupElement> {

    private EmptyClassifier() {
      super(null, "empty");
    }

    @NotNull
    @Override
    public List<Pair<LookupElement, Object>> getSortingWeights(@NotNull Iterable<? extends LookupElement> items, @NotNull ProcessingContext context) {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public Iterable<LookupElement> classify(@NotNull Iterable<? extends LookupElement> source, @NotNull ProcessingContext context) {
      //noinspection unchecked
      return (Iterable<LookupElement>)source;
    }

  }
}
