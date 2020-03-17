// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.completion.impl.CompletionSorterImpl
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.EmptyLookupItem
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.StandardPatterns
import com.intellij.util.ProcessingContext
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.hash.EqualityPolicy
import com.intellij.util.containers.hash.LinkedHashMap
import java.util.*
import kotlin.Comparator
import kotlin.math.max

open class BaseCompletionLookupArranger(@JvmField protected val myProcess: CompletionProcessEx) : LookupArranger(), CompletionLookupArranger {
  private val myFrozenItems: MutableList<LookupElement> = mutableListOf()
  private val myLimit = Registry.intValue("ide.completion.variant.limit")
  private var myOverflow = false

  private val myLocation: CompletionLocation? = null
  private val myClassifiers: MutableMap<CompletionSorterImpl, Classifier<LookupElement>> = mutableMapOf()
  private val mySorterKey: Key<CompletionSorterImpl> = Key.create("SORTER_KEY")
  private val myFinalSorter: CompletionFinalSorter = CompletionFinalSorter.newSorter()
  private var myPrefixChanges = 0

  var lastLookupPrefix: String? = null

  private fun groupItemsBySorter(source: Iterable<LookupElement>): MultiMap<CompletionSorterImpl, LookupElement> {
    val inputBySorter: MultiMap<CompletionSorterImpl, LookupElement> = MultiMap.createLinked()
    for (element in source) {
      val sorter = obtainSorter(element)
      if (sorter != null) {
        inputBySorter.putValue(sorter, element)
      }
    }
    for (sorter in inputBySorter.keySet()) {
      inputBySorter.put(sorter, sortByPresentation(inputBySorter[sorter]))
    }
    return inputBySorter
  }

  private fun obtainSorter(element: LookupElement): CompletionSorterImpl? {
    return element.getUserData(mySorterKey)
  }

  override fun getRelevanceObjects(items: Iterable<LookupElement>, hideSingleValued: Boolean): Map<LookupElement, List<Pair<String, Any>>> {
    val map: MutableMap<LookupElement, MutableList<Pair<String, Any>>> = ContainerUtil.newIdentityHashMap()
    val inputBySorter = groupItemsBySorter(items)
    var sorterNumber = 0
    inputBySorter.keySet().forEach { sorter ->
      sorterNumber++
      val thisSorterItems = inputBySorter[sorter]
      for (element in thisSorterItems) {
        map[element] = mutableListOf<Pair<String, Any>>(
          Pair("frozen", myFrozenItems.contains(element)),
          Pair("sorter", sorterNumber)
        )
      }
      val context = createContext()
      var classifier = myClassifiers[sorter]
      while (classifier != null) {
        val itemSet = ContainerUtil.newIdentityTroveSet(thisSorterItems)
        val unsortedItems = ContainerUtil.filter(myItems
        ) { lookupElement: LookupElement? ->
          itemSet.contains(
            lookupElement)
        }
        val pairs = classifier.getSortingWeights(unsortedItems, context)
        if (!hideSingleValued || !haveSameWeights(pairs)) {
          for (pair in pairs) {
            map[pair.first]?.add(Pair.create(classifier.presentableName, pair.second))
          }
        }
        classifier = classifier.next
      }
    }
    @Suppress("UNCHECKED_CAST")
    val result: MutableMap<LookupElement, List<Pair<String, Any>>> = LinkedHashMap(EqualityPolicy.IDENTITY as EqualityPolicy<in LookupElement>)
    val additional: Map<LookupElement, List<Pair<String, Any>>> = myFinalSorter.getRelevanceObjects(items)
    items.forEach { item ->
      val mainRelevance = map[item] ?: mutableListOf()
      val additionalRelevance = additional[item]
      result[item] = if (additionalRelevance == null) mainRelevance else ContainerUtil.concat(mainRelevance, additionalRelevance)
    }
    return result
  }

  fun associateSorter(element: LookupElement, sorter: CompletionSorterImpl) {
    element.putUserData(mySorterKey, sorter)
  }

  override fun addElement(element: LookupElement,
                          sorter: CompletionSorter,
                          prefixMatcher: PrefixMatcher,
                          presentation: LookupElementPresentation) {
    registerMatcher(element, prefixMatcher)
    associateSorter(element, (sorter as CompletionSorterImpl))
    addElement(element, presentation)
  }

  override fun addElement(result: CompletionResult) {
    val presentation = LookupElementPresentation()
    result.lookupElement.renderElement(presentation)
    addElement(result.lookupElement, result.sorter, result.prefixMatcher, presentation)
  }

  override fun addElement(element: LookupElement, presentation: LookupElementPresentation) {
    //StatisticsWeigher.clearBaseStatisticsInfo(element)
    val invariant = PresentationInvariant(presentation.itemText, presentation.tailText,
                                          presentation.typeText)
    element.putUserData(PRESENTATION_INVARIANT, invariant)
    val sorter = obtainSorter(element)
    var classifier: Classifier<LookupElement>? = myClassifiers[sorter]
    if (classifier == null) {
      myClassifiers[sorter!!] = sorter.buildClassifier(EmptyClassifier()).also { classifier = it }
    }
    val context = createContext()
    classifier!!.addElement(element, context)
    super.addElement(element, presentation)
    trimToLimit(context)
  }

  override fun itemSelected(lookupItem: LookupElement?, completionChar: Char) {
    myProcess.itemSelected(lookupItem, completionChar)
  }

  private fun trimToLimit(context: ProcessingContext) {
    if (myItems.size < myLimit) return
    val items = matchingItems
    val iterator: Iterator<LookupElement> = sortByRelevance(groupItemsBySorter(items)).iterator()
    val retainedSet: MutableSet<LookupElement> = ContainerUtil.newIdentityTroveSet()
    retainedSet.addAll(getPrefixItems(true))
    retainedSet.addAll(getPrefixItems(false))
    retainedSet.addAll(myFrozenItems)
    while (retainedSet.size < myLimit / 2 && iterator.hasNext()) {
      retainedSet.add(iterator.next())
    }
    if (!iterator.hasNext()) return
    val removed = retainItems(retainedSet)
    for (element in removed) {
      removeItem(element, context)
    }
    if (!myOverflow) {
      myOverflow = true
      myProcess.addAdvertisement(OVERFLOW_MESSAGE, null)
      // restart completion on any prefix change
      myProcess.addWatchedPrefix(0, StandardPatterns.string())
      if (ApplicationManager.getApplication().isUnitTestMode) printTestWarning()
    }
  }

  private fun printTestWarning() {
    System.err.println(
      "Your test might miss some lookup items, because only " + myLimit / 2 + " most relevant items are guaranteed to be shown in the lookup. You can:")
    System.err.println("1. Make the prefix used for completion longer, so that there are less suggestions.")
    System.err.println("2. Increase 'ide.completion.variant.limit' (using RegistryValue#setValue with a test root disposable).")
    System.err.println("3. Ignore this warning.")
  }

  private fun removeItem(element: LookupElement,
                         context: ProcessingContext) {
    val sorter = obtainSorter(element)
    val classifier = myClassifiers[sorter]
    classifier!!.removeElement(element, context)
  }

  private fun sortByPresentation(source: Iterable<LookupElement>): List<LookupElement> {
    val startMatches = mutableListOf<LookupElement>()
    val middleMatches = mutableListOf<LookupElement>()
    for (element in source) {
      (if (itemMatcher(element).isStartMatch(element)) startMatches else middleMatches).add(element)
    }
    ContainerUtil.sort(startMatches, BY_PRESENTATION_COMPARATOR)
    ContainerUtil.sort(middleMatches, BY_PRESENTATION_COMPARATOR)
    startMatches.addAll(middleMatches)
    return startMatches
  }

  protected open fun isAlphaSorted(): Boolean = false

  override fun arrangeItems(): Pair<MutableList<LookupElement>, Int> {
    val dummyListPresenter: LookupElementListPresenter = object : LookupElementListPresenter {
      override fun getAdditionalPrefix(): String {
        return ""
      }

      override fun getCurrentItem(): LookupElement? {
        return null
      }

      override fun getCurrentItemOrEmpty(): LookupElement? {
        return null
      }

      override fun isSelectionTouched(): Boolean {
        return false
      }

      override fun getSelectedIndex(): Int {
        return 0
      }

      override fun getLastVisibleIndex(): Int {
        return 0
      }

      override fun getLookupFocusDegree(): LookupFocusDegree {
        return LookupFocusDegree.FOCUSED
      }

      override fun isShown(): Boolean {
        return true
      }
    }
    return doArrangeItems(dummyListPresenter, false)
  }

  override fun arrangeItems(lookup: Lookup, onExplicitAction: Boolean): Pair<MutableList<LookupElement>, Int> {
    return doArrangeItems(lookup as LookupElementListPresenter, onExplicitAction)
  }

  private fun doArrangeItems(lookup: LookupElementListPresenter, onExplicitAction: Boolean): Pair<MutableList<LookupElement>, Int> {
    val items = matchingItems
    var sortedByRelevance: Iterable<LookupElement> = sortByRelevance(groupItemsBySorter(items))
    if (sortedByRelevance.iterator().hasNext()) {
      sortedByRelevance = myFinalSorter.sort(sortedByRelevance, Objects.requireNonNull(myProcess.parameters))
    }
    val relevantSelection = findMostRelevantItem(sortedByRelevance)
    val listModel =
      if (isAlphaSorted()) sortByPresentation(items)
      else fillModelByRelevance(lookup, ContainerUtil.newIdentityTroveSet(items), sortedByRelevance, relevantSelection)
    val toSelect: Int = getItemToSelect(lookup, listModel, onExplicitAction, relevantSelection)
    LOG.assertTrue(toSelect >= 0)
    return Pair(listModel.toMutableList(), toSelect)
  }

  private fun fillModelByRelevance(lookup: LookupElementListPresenter,
                                   items: Set<LookupElement>,
                                   sortedElements: Iterable<LookupElement>,
                                   relevantSelection: LookupElement?): List<LookupElement> {
    val byRelevance = sortedElements.iterator()
    val model = mutableSetOf<LookupElement>()
    addPrefixItems(model)
    addFrozenItems(items, model)
    if (model.size < MAX_PREFERRED_COUNT) {
      addSomeItems(model, byRelevance, Condition { model.size >= MAX_PREFERRED_COUNT })
    }
    addCurrentlySelectedItemToTop(lookup, items, model)
    freezeTopItems(lookup, model)
    ensureItemAdded(items, model, byRelevance, lookup.currentItem)
    ensureItemAdded(items, model, byRelevance, relevantSelection)
    ContainerUtil.addAll(model, byRelevance)
    return model.toList()
  }

  private fun freezeTopItems(lookup: LookupElementListPresenter, model: MutableSet<LookupElement>) {
    myFrozenItems.clear()
    if (lookup.isShown) {
      myFrozenItems.addAll(model)
    }
  }

  private fun addFrozenItems(items: Set<LookupElement>, model: MutableSet<LookupElement>) {
    val iterator = myFrozenItems.iterator()
    while (iterator.hasNext()) {
      val element = iterator.next()
      if (!element.isValid || !items.contains(element)) {
        iterator.remove()
      }
    }
    model.addAll(myFrozenItems)
  }

  private fun addPrefixItems(model: MutableSet<LookupElement>) {
    ContainerUtil.addAll(model, sortByRelevance(groupItemsBySorter(getPrefixItems(true))))
    ContainerUtil.addAll(model, sortByRelevance(groupItemsBySorter(getPrefixItems(false))))
  }

  private fun sortByRelevance(inputBySorter: MultiMap<CompletionSorterImpl, LookupElement>): Iterable<LookupElement> {
    if (inputBySorter.isEmpty) return emptyList()
    val byClassifier: MutableList<Iterable<LookupElement>> = ArrayList()
    for (sorter in myClassifiers.keys) {
      val context = createContext()
      byClassifier.add(myClassifiers[sorter]!!.classify(inputBySorter[sorter], context))
    }
    return ContainerUtil.concat(*byClassifier.toTypedArray())
  }

  private fun createContext(): ProcessingContext {
    val context = ProcessingContext()
    context.put(CompletionLookupArranger.PREFIX_CHANGES, myPrefixChanges)
    context.put(CompletionLookupArranger.WEIGHING_CONTEXT, this)
    return context
  }

  override fun createEmptyCopy(): LookupArranger = BaseCompletionLookupArranger(myProcess)

  private fun getItemToSelect(lookup: LookupElementListPresenter,
                              items: List<LookupElement>,
                              onExplicitAction: Boolean,
                              mostRelevant: LookupElement?): Int {
    if (items.isEmpty() || lookup.lookupFocusDegree == LookupFocusDegree.UNFOCUSED) {
      return 0
    }
    if (lookup.isSelectionTouched || !onExplicitAction) {
      val lastSelection = lookup.currentItem
      val old = ContainerUtil.indexOfIdentity(items, lastSelection)
      if (old >= 0) {
        return old
      }
      val selectedValue = lookup.currentItemOrEmpty
      if (selectedValue is EmptyLookupItem && selectedValue.isLoading) {
        val index = lookup.selectedIndex
        if (index >= 0 && index < items.size) {
          return index
        }
      }
      for (i in items.indices) {
        val invariant = PRESENTATION_INVARIANT.get(items[i])
        if (invariant != null && invariant == PRESENTATION_INVARIANT.get(lastSelection)) {
          return i
        }
      }
    }
    val exactMatch = getBestExactMatch(items)
    return max(0, ContainerUtil.indexOfIdentity(items, exactMatch ?: mostRelevant))
  }

  protected open fun getExactMatches(items: List<LookupElement>): List<LookupElement> {
    var editor = myProcess.parameters.editor
    if (editor is EditorWindow) editor = editor.delegate
    val selectedText = editor.selectionModel.selectedText
    val exactMatches: MutableList<LookupElement> = SmartList()
    for (i in items.indices) {
      val item = items[i]
      if (isPrefixItem(item, true) || item.lookupString == selectedText) {
        exactMatches.add(item)
      }
    }
    return exactMatches
  }

  private fun getBestExactMatch(items: List<LookupElement>): LookupElement? {
    val exactMatches = getExactMatches(items)
    if (exactMatches.isEmpty()) return null
    return if (exactMatches.size == 1) {
      exactMatches[0]
    }
    else sortByRelevance(groupItemsBySorter(exactMatches)).iterator().next()
  }

  private fun findMostRelevantItem(sorted: Iterable<LookupElement>): LookupElement? {
    val skippers = CompletionPreselectSkipper.EP_NAME.extensions
    for (element in sorted) {
      if (!shouldSkip(skippers, element)) {
        return element
      }
    }
    return null
  }

  private fun shouldSkip(skippers: Array<CompletionPreselectSkipper>, element: LookupElement): Boolean {
    var location = myLocation
    if (location == null) {
      location = CompletionLocation(Objects.requireNonNull(myProcess.parameters))
    }
    for (skipper in skippers) {
      if (skipper.skipElement(element, location)) {
        if (LOG.isDebugEnabled) {
          LOG.debug("Skipped element $element by $skipper")
        }
        return true
      }
    }
    return false
  }

  override fun prefixChanged(lookup: Lookup) {
    myPrefixChanges++
    myFrozenItems.clear()
    super.prefixChanged(lookup)
  }

  override fun prefixTruncated(lookup: LookupEx, hideOffset: Int) {
    if (hideOffset < lookup.editor.caretModel.offset) {
      myProcess.scheduleRestart()
      return
    }
    myProcess.prefixUpdated()
    lookup.hideLookup(false)
  }

  override fun isCompletion(): Boolean {
    return true
  }

  companion object {
    private val LOG = logger<CompletionLookupArranger>()
    private val PRESENTATION_INVARIANT: Key<PresentationInvariant> = Key.create("PRESENTATION_INVARIANT")
    @JvmField
    val FORCE_MIDDLE_MATCH: Key<out Any> = Key.create("FORCE_MIDDLE_MATCH")

    private val BY_PRESENTATION_COMPARATOR: Comparator<LookupElement> = Comparator { o1, o2 ->
      val invariant = PRESENTATION_INVARIANT.get(o1)
      assert(invariant != null)
      invariant.compareTo(PRESENTATION_INVARIANT.get(o2))
    }
    const val MAX_PREFERRED_COUNT = 5
    const val OVERFLOW_MESSAGE = "Not all variants are shown, please type more letters to see the rest"

    private fun haveSameWeights(pairs: List<Pair<LookupElement, Any>>): Boolean {
      if (pairs.isEmpty()) return true
      for (i in 1 until pairs.size) {
        if (!Comparing.equal(pairs[i].second, pairs[0].second)) {
          return false
        }
      }
      return true
    }

    private fun ensureItemAdded(items: Set<LookupElement>,
                                model: MutableSet<LookupElement>,
                                byRelevance: Iterator<LookupElement>,
                                item: LookupElement?) {
      if (item != null && items.contains(item) && !model.contains(item)) {
        addSomeItems(model, byRelevance, Condition { lastAdded: LookupElement -> lastAdded === item })
      }
    }

    private fun addCurrentlySelectedItemToTop(lookup: LookupElementListPresenter,
                                              items: Set<LookupElement>,
                                              model: MutableSet<LookupElement>) {
      if (!lookup.isSelectionTouched) {
        val lastSelection = lookup.currentItem ?: return
        if (items.contains(lastSelection)) {
          model.add(lastSelection)
        }
      }
    }

    private fun addSomeItems(model: MutableSet<LookupElement>,
                             iterator: Iterator<LookupElement>,
                             stopWhen: Condition<LookupElement>) {
      while (iterator.hasNext()) {
        val item = iterator.next()
        model.add(item)
        if (stopWhen.value(item)) {
          break
        }
      }
    }

    private class EmptyClassifier : Classifier<LookupElement>(null, "empty") {
      override fun getSortingWeights(items: Iterable<LookupElement>,
                                     context: ProcessingContext): List<Pair<LookupElement, Any>> {
        return emptyList()
      }

      override fun classify(source: Iterable<LookupElement>,
                            context: ProcessingContext): Iterable<LookupElement> {
        return source
      }
    }
  }
}