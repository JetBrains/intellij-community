// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.Pair
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.StatusText
import javax.swing.DefaultListModel
import javax.swing.JCheckBox
import javax.swing.JPanel

/**
 * This component represents a list of checkboxes.
 */
@Suppress("LeakingThis")
abstract class OptionalChooserComponent<T>(items: MutableList<Pair<T, Boolean>>) : CheckBoxListListener, ComponentWithEmptyText {
  private val listModel = DefaultListModel<JCheckBox>()

  open val list: CheckBoxList<*> = CheckBoxList<Any>(listModel, this).apply {
    border = null
  }

  open val contentPane: JPanel = panel {
    row {
      scrollCell(list)
        .align(Align.FILL)
    }.resizableRow()
  }

  private lateinit var initialList: MutableList<Pair<T, Boolean>>
  private lateinit var workingList: ArrayList<Pair<T, Boolean>>

  init {
    setInitialList(items)
    workingList = ArrayList(initialList)

    // fill list
    reset()
  }

  override fun getEmptyText(): StatusText = list.emptyText

  override fun checkBoxSelectionChanged(index: Int, value: Boolean) {
    workingList[index] = Pair(workingList[index].first, value)
  }

  open fun reset() {
    workingList = ArrayList(initialList)
    refresh()
  }

  protected abstract fun createCheckBox(value: T, checked: Boolean): JCheckBox

  open var selectedIndex: Int
    get() = list.selectedIndex
    set(index) {
      list.selectedIndex = index
    }

  open fun removeAt(index: Int): Boolean {
    currentModel.removeAt(index)
    refresh()

    if (index < currentModel.size) {
      selectedIndex = index
      return true
    }
    else if (index > 0) {
      selectedIndex = index - 1
      return true
    }
    return false
  }

  open fun removeSelected(): Boolean {
    val index = selectedIndex
    if (index != -1) {
      return removeAt(index)
    }
    return false
  }

  open fun isModified(): Boolean = workingList != initialList

  open fun setInitialList(list: MutableList<Pair<T, Boolean>>) {
    initialList = list
  }

  open val currentModel: ArrayList<Pair<T, Boolean>>
    get() = workingList

  open fun apply() {
    initialList.clear()
    initialList.addAll(workingList)
  }

  open fun refresh() {
    listModel.clear()
    for (pair in workingList) {
      listModel.addElement(createCheckBox(pair.first, pair.second))
    }
  }
}
