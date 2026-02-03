// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ui.speedSearch.FilteringListModel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Dimension
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JList
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

@ApiStatus.Internal
@VisibleForTesting
class WideSelectionListCache(private val list: JList<*>) {

  private var modelChanged = false

  /**
   * Key is a value from the [list].
   * Don't cache `null` value because of using [MutableMap.getOrPut]
   */
  @VisibleForTesting
  val preferredSizeCache: MutableMap<Any, Dimension> = IdentityHashMap()

  private val listDataListener = object : ListDataListener {
    override fun intervalAdded(e: ListDataEvent?) {
      // No need modelChanged, the cache will be filled later when needed
      purgeIfNeeded()
    }

    override fun intervalRemoved(e: ListDataEvent) {
      modelChanged = true
      // No need purgeIfNeeded, because `replaceAll` method does `removeAllItems` and `addAllItems`,
      // which leads to unnecessary full cache rebuilding.
    }

    override fun contentsChanged(e: ListDataEvent) {
      modelChanged = true
      purgeIfNeeded()
    }
  }

  private val modelChangeListener = PropertyChangeListener { evt ->
    uninstallListDataListener(evt.oldValue as ListModel<*>?)
    installListDataListener(evt.newValue as ListModel<*>?)
    clear()
  }

  fun installListeners() {
    list.addPropertyChangeListener("model", modelChangeListener)
    installListDataListener(list.model)
  }

  fun uninstallListeners() {
    list.removePropertyChangeListener("model", modelChangeListener)
    uninstallListDataListener(list.model)
    clear()
  }

  fun getCachedPreferredSizeOrCalculate(value: Any?, useCache: Boolean, supplier: () -> Dimension): Dimension {
    if (!useCache) {
      clear()
      return supplier()
    }

    purgeIfNeeded()

    if (value == null) {
      return supplier()
    }

    return preferredSizeCache.getOrPut(value, supplier)
  }

  private fun clear() {
    modelChanged = false
    preferredSizeCache.clear()
  }

  private fun purgeIfNeeded() {
    if (!modelChanged) {
      return
    }

    modelChanged = false
    val model = list.model.originalModel

    if (model == null) {
      preferredSizeCache.clear()
      return
    }

    val existingValues = Collections.newSetFromMap(IdentityHashMap<Any?, Boolean>())
    for (i in 0 until model.size) {
      existingValues.add(model.getElementAt(i))
    }
    preferredSizeCache.keys.retainAll(existingValues)
  }

  private fun installListDataListener(model: ListModel<*>?) {
    model.originalModel?.addListDataListener(listDataListener)
  }

  private fun uninstallListDataListener(model: ListModel<*>?) {
    model.originalModel?.removeListDataListener(listDataListener)
  }

  private val ListModel<*>?.originalModel: ListModel<*>?
    get() {
      return when (this) {
        null -> null
        is FilteringListModel<*> -> originalModel
        else -> this
      }
    }
}