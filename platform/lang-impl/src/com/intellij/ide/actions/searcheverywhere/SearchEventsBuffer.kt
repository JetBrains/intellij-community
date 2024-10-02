// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEventsBuffer {

  private val addedElements = mutableListOf<SearchEverywhereFoundElementInfo>()
  private val removedElements = mutableListOf<SearchEverywhereFoundElementInfo>()
  private val finalEventsMap = hashMapOf<SearchEverywhereContributor<*>, FinalEventType>()

  fun addElements(list: List<SearchEverywhereFoundElementInfo>) {
    // adding new elements should drop contributorWaits event from buffer
    list.map { it.contributor }.distinct().forEach{ eraseWaitingStatus(it) }

    val toAdd = removeElementsFormList(list, removedElements)
    addedElements.addAll(toAdd)

    SearchEverywhereMlService.getInstance()?.let { mlService ->
      list.forEach { item ->
        mlService.addBufferedTimestamp(item, System.currentTimeMillis())
      }
    }
  }

  fun removeElements(list: List<SearchEverywhereFoundElementInfo>) {
    val toRemove = removeElementsFormList(list, addedElements)
    removedElements.addAll(toRemove)
  }

  fun contributorWaits(contributor: SearchEverywhereContributor<*>) {
    finalEventsMap[contributor] = FinalEventType.WAITS
  }

  fun contributorFinished(contributor: SearchEverywhereContributor<*>, hasMore: Boolean) {
    finalEventsMap[contributor] = if (hasMore) FinalEventType.FINISHED_WITH_MORE else FinalEventType.FINISHED_WITHOUT_MORE
  }

  fun flushBuffer(destinationListener: SearchListener) {
    if (!addedElements.isEmpty()) destinationListener.elementsAdded(addedElements)
    if (!removedElements.isEmpty())destinationListener.elementsRemoved(removedElements)
    finalEventsMap.forEach { (contributor, type) ->
      when (type) {
        FinalEventType.FINISHED_WITH_MORE -> destinationListener.contributorFinished(contributor, true)
        FinalEventType.FINISHED_WITHOUT_MORE -> destinationListener.contributorFinished(contributor, false)
        FinalEventType.WAITS -> destinationListener.contributorWaits(contributor)
      }
    }
    clearBuffer()
  }

  fun clearBuffer() {
    addedElements.clear()
    removedElements.clear()
    finalEventsMap.clear()
  }

  private fun removeElementsFormList(newElements: List<SearchEverywhereFoundElementInfo>,
                                     existedElements: MutableList<SearchEverywhereFoundElementInfo>): List<SearchEverywhereFoundElementInfo> {
    val res = mutableListOf<SearchEverywhereFoundElementInfo>()
    newElements.forEach { newElement ->
      if (!existedElements.removeIf { existing -> areTheSame(newElement, existing) }) res.add(newElement)
    }

    return res
  }

  private fun areTheSame(removing: SearchEverywhereFoundElementInfo, existing: SearchEverywhereFoundElementInfo): Boolean {
    return existing.getContributor() === removing.getContributor() && existing.getElement() === removing.getElement()
  }

  private fun eraseWaitingStatus(contributor: SearchEverywhereContributor<*>) {
    if (finalEventsMap[contributor] == FinalEventType.WAITS) finalEventsMap.remove(contributor)
  }

}

private enum class FinalEventType {
  WAITS, FINISHED_WITH_MORE, FINISHED_WITHOUT_MORE
}