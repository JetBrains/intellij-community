// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.Disposable
import com.intellij.platform.lvcs.impl.ActivityData
import com.intellij.platform.lvcs.impl.ActivityItem
import com.intellij.platform.lvcs.impl.ActivityPresentation
import com.intellij.platform.lvcs.impl.ActivitySelection
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.hover.ListHoverListener
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*

@ApiStatus.Internal
class ActivityList(presentationFunction: (item: ActivityItem) -> ActivityPresentation?) : JBList<ActivityItem>() {
  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  private var data = ActivityData.EMPTY
  private var visibleItems: Set<ActivityItem>? = null

  val selection: ActivitySelection get() = ActivitySelection(selectedIndices.map { model.getElementAt(it) }, data)

  init {
    cellRenderer = ActivityItemRenderer(presentationFunction)
    ListHoverListener.DEFAULT.addTo(this)
    addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      eventDispatcher.multicaster.onSelectionChanged(selection)
    }
    addKeyListener(MyEnterListener())
    MyDoubleClickListener().installOn(this)
    getAccessibleContext().accessibleName = LocalHistoryBundle.message("activity.list.accessible.name")
  }

  fun setData(data: ActivityData) {
    doWithPreservedSelection {
      this.data = data
      val filteringModel = FilteringListModel(createDefaultListModel(data.items))
      setModel(filteringModel)
      filteringModel.setFilter { visibleItems?.contains(it) != false }
    }
  }

  fun setVisibleItems(items: Set<ActivityItem>?) {
    doWithPreservedSelection {
      visibleItems = items
      (model as? FilteringListModel)?.refilter()
    }
  }

  private fun doWithPreservedSelection(task: () -> Unit) {
    val selection = Selection()
    try {
      task()
    }
    finally {
      selection.restore()
    }
  }

  fun moveSelection(forward: Boolean) {
    val step = if (forward) 1 else -1
    val newIndex = (model.size + selectionModel.leadSelectionIndex + step) % model.size
    ScrollingUtil.selectItem(this, newIndex)
  }

  fun addListener(listener: Listener, parent: Disposable) {
    eventDispatcher.addListener(listener, parent)
  }

  interface Listener : EventListener {
    fun onSelectionChanged(selection: ActivitySelection)
    fun onEnter(): Boolean
    fun onDoubleClick(): Boolean
  }

  private inner class Selection {
    val selectedItems = selectionModel.selectedIndices.mapTo(mutableSetOf()) { model.getElementAt(it) }

    fun restore() {
      val newIndices = 0.until(model.size).filter { selectedItems.contains(model.getElementAt(it)) }

      selectionModel.valueIsAdjusting = true
      selectionModel.clearSelection()
      for (index in newIndices) {
        selectionModel.addSelectionInterval(index, index)
      }
      if (selectionModel.isSelectionEmpty && model.size > 0) selectionModel.addSelectionInterval(0, 0)
      selectionModel.valueIsAdjusting = false
    }
  }

  private inner class MyEnterListener : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      if (KeyEvent.VK_ENTER != e.keyCode || e.modifiers != 0) return
      if (selectedIndices.isEmpty()) return
      if (eventDispatcher.listeners.firstOrNull { it.onEnter() } != null) e.consume()
    }
  }

  private inner class MyDoubleClickListener : DoubleClickListener() {
    override fun onDoubleClick(e: MouseEvent): Boolean {
      if (selectedIndices.isEmpty()) return false
      return eventDispatcher.listeners.firstOrNull { it.onDoubleClick() } != null
    }
  }
}