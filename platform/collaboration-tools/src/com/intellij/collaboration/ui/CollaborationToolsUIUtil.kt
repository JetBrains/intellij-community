// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import javax.swing.JList
import javax.swing.event.DocumentEvent

object CollaborationToolsUIUtil {

  /**
   * Connects [searchTextField] to a [list] to be used as a filter
   */
  fun <T> attachSearch(list: JList<T>, searchTextField: SearchTextField, searchBy: (T) -> String) {
    val speedSearch = SpeedSearch(false)
    val filteringListModel = NameFilteringListModel<T>(list.model, searchBy, speedSearch::shouldBeShowing, speedSearch.filter::orEmpty)
    list.model = filteringListModel

    searchTextField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = speedSearch.updatePattern(searchTextField.text)
    })

    speedSearch.addChangeListener {
      val prevSelection = list.selectedValue // save to restore the selection on filter drop
      filteringListModel.refilter()
      if (filteringListModel.size > 0) {
        val fullMatchIndex = if (speedSearch.isHoldingFilter) filteringListModel.closestMatchIndex
        else filteringListModel.getElementIndex(prevSelection)
        if (fullMatchIndex != -1) {
          list.selectedIndex = fullMatchIndex
        }

        if (filteringListModel.size <= list.selectedIndex || !filteringListModel.contains(list.selectedValue)) {
          list.selectedIndex = 0
        }
      }
    }

    ScrollingUtil.installActions(list)
    ScrollingUtil.installActions(list, searchTextField.textEditor)
  }
}