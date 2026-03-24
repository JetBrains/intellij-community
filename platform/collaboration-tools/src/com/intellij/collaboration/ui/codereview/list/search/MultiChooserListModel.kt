// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import javax.swing.AbstractListModel

/**
 * A special list model which allows choosing multiple items from the list
 * Ensures items are not added to the list twice
 */
internal class MultiChooserListModel<T> : AbstractListModel<T>() {
  private val chosenItems = mutableSetOf<T>()
  private val itemsSet = mutableSetOf<T>()
  private val items = mutableListOf<T>()

  override fun getSize(): Int = items.size

  override fun getElementAt(index: Int): T? = items.getOrNull(index)

  fun isChosen(value: T): Boolean = chosenItems.contains(value)

  fun getChosenItems(): List<T> = items.filter { it in chosenItems }

  fun setChosen(toChose: Collection<T>) {
    chosenItems.clear()
    toChose.forEach {
      val idx = items.indexOf(it)
      if (idx >= 0) {
        chosenItems.add(it)
        fireContentsChanged(this, idx, idx)
      }
    }
  }

  fun toggleChosen(item: T) {
    val idx = items.indexOf(item)
    if (idx >= 0) {
      if (chosenItems.contains(item)) {
        chosenItems.remove(item)
      }
      else {
        chosenItems.add(item)
      }
      fireContentsChanged(this, idx, idx)
    }
  }

  fun add(newList: List<T>) {
    val lastIndex = items.lastIndex
    var count = 0
    newList.forEach {
      if (itemsSet.add(it)) {
        items.add(it)
        count++
      }
    }
    if (count != 0) {
      fireIntervalAdded(this, 0, lastIndex + count)
    }
  }

  /**
   * Updates the list of items, ensuring that chosen items stay in the list
   */
  fun retainChosenAndUpdate(newList: List<T>) {
    val oldSize = items.size

    items.clear()
    itemsSet.clear()

    chosenItems.forEach { chosen ->
      items.add(chosen)
      itemsSet.add(chosen)
    }

    newList.forEach { item ->
      if (itemsSet.add(item)) {
        items.add(item)
      }
    }

    val newSize = items.size
    when {
      oldSize == 0 && newSize > 0 -> {
        fireIntervalAdded(this, 0, newSize - 1)
      }
      newSize == 0 && oldSize > 0 -> {
        fireIntervalRemoved(this, 0, oldSize - 1)
      }
      oldSize != newSize -> {
        val minSize = minOf(oldSize, newSize)
        if (minSize > 0) {
          fireContentsChanged(this, 0, minSize - 1)
        }
        if (newSize > oldSize) {
          fireIntervalAdded(this, oldSize, newSize - 1)
        }
        else {
          fireIntervalRemoved(this, newSize, oldSize - 1)
        }
      }
      else -> {
        fireContentsChanged(this, 0, newSize - 1)
      }
    }
  }

  fun removeAllExceptChosen() {
    val oldSize = items.size
    items.clear()
    itemsSet.clear()
    if (oldSize != 0) {
      fireIntervalRemoved(this, 0, oldSize - 1)
    }

    chosenItems.forEach { chosen ->
      items.add(chosen)
      itemsSet.add(chosen)
    }
    if (items.isNotEmpty()) {
      fireIntervalAdded(this, 0, items.lastIndex)
    }
  }
}