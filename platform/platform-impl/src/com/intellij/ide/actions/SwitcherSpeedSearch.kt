// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.Switcher.SwitcherPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.speedSearch.NameFilteringListModel
import javax.swing.ListModel

class SwitcherSpeedSearch private constructor(switcher: SwitcherPanel) : SpeedSearchBase<SwitcherPanel>(switcher, null) {
  fun updateEnteredPrefix(): Unit? = searchField?.let {
    val text = it.text ?: ""
    when (text.length > 1) {
      true -> {
        it.text = text.dropLast(1)
        fireStateChanged()
      }
      else -> {
        it.text = ""
        hidePopup()
      }
    }
  }

  fun <T : SwitcherListItem> wrap(model: ListModel<T>): ListModel<T> =
    NameFilteringListModel(model,
                           { it.mainText },
                           { !isPopupActive || compare(it, enteredPrefix) },
                           { (enteredPrefix ?: "") })

  private val files
    get() = myComponent.files

  private val windows
    get() = myComponent.toolWindows

  override fun getSelectedIndex(): Int = when (windows.selectedIndex >= 0) {
    true -> windows.selectedIndex + files.itemsCount
    else -> files.selectedIndex
  }

  override fun getElementText(element: Any?): String = (element as? SwitcherListItem)?.mainText ?: ""

  override fun getElementCount(): Int = files.itemsCount + windows.itemsCount

  override fun getElementAt(index: Int): Any? = when {
    index < 0 -> null
    index < files.itemsCount -> files.model.getElementAt(index)
    index < elementCount -> windows.model.getElementAt(index - files.itemsCount)
    else -> null
  }

  override fun selectElement(element: Any?, selectedText: String) {
    val fileElement = element is SwitcherVirtualFile
    val first = if (!fileElement) files else windows
    val second = if (fileElement) files else windows
    if (!first.isSelectionEmpty) first.clearSelection()
    second.clearSelection()
    if (element == null) return
    second.setSelectedValue(element, true)
    second.requestFocusInWindow()
  }

  override fun findElement(pattern: String): Any? {
    val windowsFocused = windows.isFocusOwner
    val first = if (!windowsFocused) files else windows
    val second = if (windowsFocused) files else windows
    return findElementIn(first.model, pattern) ?: findElementIn(second.model, pattern)
  }

  private fun <T> findElementIn(model: ListModel<T>, pattern: String): T? {
    var foundElement: T? = null
    var foundDegree = 0
    for (i in 0 until model.size) {
      val element = model.getElementAt(i)
      val text = getElementText(element)
      if (text.isEmpty()) continue
      val degree = comparator.matchingDegree(pattern, text)
      if (foundElement == null || foundDegree < degree) {
        foundElement = element
        foundDegree = degree
      }
    }
    return foundElement
  }

  init {
    comparator = SpeedSearchComparator(Registry.`is`("ide.recent.files.speed.search.beginning"),
                                       Registry.`is`("ide.recent.files.speed.search.camel.case"))
    addChangeListener {
      if (myComponent.project.isDisposed) {
        myComponent.popup?.cancel()
      }
      else {
        val isPopupActive = isPopupActive
        val element = if (isPopupActive) null else getElementAt(selectedIndex)

        (files.model as? NameFilteringListModel<*>)?.refilter()
        (windows.model as? NameFilteringListModel<*>)?.refilter()

        if (isPopupActive && files.isEmpty && windows.isEmpty) {
          files.setEmptyText(message("recent.files.speed.search.empty.text"))
          windows.setEmptyText("")
        }
        else {
          files.setEmptyText(message("recent.files.file.list.empty.text"))
          windows.setEmptyText(message("recent.files.tool.window.list.empty.text"))
        }
        when {
          isPopupActive -> refreshSelection()
          else -> selectElement(element ?: getElementAt(0), "")
        }
      }
    }
  }

  companion object {
    internal fun installOn(switcher: SwitcherPanel): SwitcherSpeedSearch {
      val search = SwitcherSpeedSearch(switcher)
      search.setupListeners()
      return search
    }
  }
}
