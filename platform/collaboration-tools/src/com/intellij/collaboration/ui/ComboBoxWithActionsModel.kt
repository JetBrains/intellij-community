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

internal fun <T : Any> MutableCollectionComboBoxModel<T>.setItems(value: List<T>) {
  val delta = CollectionDelta(items, value)
  delta.removedItems.forEach { removeElement(it) }
  add(delta.newItems.toList())
}

internal class ComboBoxWithActionsModel<T>
  : ComboBoxModel<ComboBoxWithActionsModel.Item<T>> {

  private val itemsModel = MutableCollectionComboBoxModel<Item.Wrapper<T>>()
  private val listEventDispatcher = EventDispatcher.create(ListDataListener::class.java)

  var items: List<T>
    get() = itemsModel.items.map { it.wrappee }
    set(value) {
      itemsModel.setItems(value.map { Item.Wrapper(it) })
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

  override fun getSelectedItem(): Item.Wrapper<T>? = itemsModel.selectedItem.asSafely<Item.Wrapper<T>>()

  override fun setSelectedItem(item: Any?) {
    if (item is Item.Action<*>) {
      val action = item.action
      if (action.isEnabled) action.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, null))
      return
    }
    itemsModel.selectedItem = item.asSafely<Item.Wrapper<T>>()
  }

  override fun getSize() = itemsModel.size + actions.size

  override fun getElementAt(index: Int): Item<T> {
    if (index in 0 until itemsModel.size) {
      return itemsModel.getElementAt(index)
    }
    val actionIndex = index - itemsModel.size
    if (actionIndex in actions.indices) {
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