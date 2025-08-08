// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ui.speedSearch.FilteringListModel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Dimension
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JList
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

@ApiStatus.Internal
@TestOnly
class WideSelectionListCache(private val list: JList<*>) {

  /**
   * Key is a value from the [list].
   * Don't cache `null` value because of using [MutableMap.getOrPut]
   */
  @ApiStatus.Internal
  @TestOnly
  val preferredSizeCache: MutableMap<Any, Dimension> = IdentityHashMap()

  private val listDataListener = object : ListDataListener {
    override fun intervalAdded(e: ListDataEvent?) {}

    override fun intervalRemoved(e: ListDataEvent) {
      purge()
    }

    override fun contentsChanged(e: ListDataEvent) {
      purge()
    }
  }

  private val modelChangeListener = PropertyChangeListener { evt ->
    uninstallListDataListener(evt.oldValue as ListModel<*>?)
    installListDataListener(evt.newValue as ListModel<*>?)
    preferredSizeCache.clear()
  }

  fun installListeners() {
    list.addPropertyChangeListener("model", modelChangeListener)
    installListDataListener(list.model)
  }

  fun uninstallListeners() {
    list.removePropertyChangeListener("model", modelChangeListener)
    uninstallListDataListener(list.model)
    preferredSizeCache.clear()
  }

  fun getCachedPreferredSizeOrCalculate(value: Any?, useCache: Boolean, supplier: () -> Dimension): Dimension {
    if (!useCache || value == null) {
      return supplier()
    }
    return preferredSizeCache.getOrPut(value, supplier)
  }

  private fun purge() {
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