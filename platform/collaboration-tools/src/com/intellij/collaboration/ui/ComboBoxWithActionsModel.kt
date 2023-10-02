// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.collaboration.util.CollectionDelta
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.util.EventDispatcher
import com.intellij.util.asSafely
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.ComboBoxModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.properties.Delegates

class ComboBoxWithActionsModel<T : Any>(private val actionsFirst: Boolean = false)
  : ComboBoxModel<ComboBoxWithActionsModel.Item<T>> {

  private val itemsModel = MutableCollectionComboBoxModel<T>()
  private val listEventDispatcher = EventDispatcher.create(ListDataListener::class.java)

  var items: List<T>
    get() = itemsModel.items
    set(value) {
      val delta = CollectionDelta<T>(itemsModel.items, value)
      delta.removedItems.forEach { itemsModel.remove(it) }
      delta.newItems.forEach { itemsModel.addElement(it) }
    }

  var actions: List<Action> by Delegates.observable(emptyList()) { _, oldValue, newValue ->
    if (oldValue.isNotEmpty()) {
      listEventDispatcher.multicaster.intervalRemoved(
        ListDataEvent(this, ListDataEvent.INTERVAL_REMOVED, itemsModel.size, itemsModel.size + oldValue.lastIndex))
    }
    if (newValue.isNotEmpty()) {
      listEventDispatcher.multicaster.intervalAdded(
        ListDataEvent(this, ListDataEvent.INTERVAL_REMOVED, itemsModel.size, itemsModel.size + newValue.lastIndex))
    }
  }

  init {
    itemsModel.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {
        listEventDispatcher.multicaster.intervalAdded(e)
      }

      override fun intervalRemoved(e: ListDataEvent) {
        listEventDispatcher.multicaster.intervalRemoved(e)
      }

      override fun contentsChanged(e: ListDataEvent) {
        listEventDispatcher.multicaster.contentsChanged(e)
      }
    })
  }

  override fun getSelectedItem(): Item.Wrapper<T>? = itemsModel.selectedItem?.let { Item.Wrapper(it as T) }

  override fun setSelectedItem(item: Any?) {
    if (item is Item.Action<*>) {
      val action = item.action
      if (action.isEnabled) action.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, null))
      return
    }
    itemsModel.selectedItem = item.asSafely<Item.Wrapper<T>>()?.wrappee
  }

  override fun getSize() = itemsModel.size + actions.size

  override fun getElementAt(index: Int): Item<T> {
    val itemIndices = if (!actionsFirst) 0 until itemsModel.size else actions.size until (itemsModel.size + actions.size)
    val actionIndices = if (!actionsFirst) itemsModel.size until (itemsModel.size + actions.size) else actions.indices

    if (index in itemIndices) {
      return itemsModel.getElementAt(index - itemIndices.first).let { Item.Wrapper(it) }
    }
    if (index in actionIndices) {
      val actionIndex = index - actionIndices.first
      return actions[actionIndex].let { Item.Action(it, actionIndex == 0 && itemsModel.size != 0) }
    }
    error("Invalid index $index")
  }

  override fun addListDataListener(l: ListDataListener) = listEventDispatcher.addListener(l)
  override fun removeListDataListener(l: ListDataListener) = listEventDispatcher.removeListener(l)

  sealed class Item<T> {
    data class Wrapper<T>(val wrappee: T) : Item<T>()
    class Action<T>(val action: javax.swing.Action, val needSeparatorAbove: Boolean) : Item<T>()
  }
}