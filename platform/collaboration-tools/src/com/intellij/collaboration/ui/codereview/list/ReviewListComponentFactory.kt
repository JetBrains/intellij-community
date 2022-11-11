// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list

import com.intellij.collaboration.ui.util.JListHoveredRowMaterialiser
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import javax.swing.ListModel
import javax.swing.ListSelectionModel

class ReviewListComponentFactory<T>(private val listModel: ListModel<T>) {
  fun create(itemPresenter: (T) -> ReviewListItemPresentation): JBList<T> {
    val listCellRenderer = ReviewListCellRenderer(itemPresenter)
    return JBList(listModel).apply {
      emptyText.clear()
      selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
      cellRenderer = listCellRenderer

      setExpandableItemsEnabled(false)
    }.also {
      ScrollingUtil.installActions(it)
      ListUiUtil.Selection.installSelectionOnFocus(it)
      ListUiUtil.Selection.installSelectionOnRightClick(it)
      ClientProperty.put(it, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(listCellRenderer))

      JListHoveredRowMaterialiser.install(it, ReviewListCellRenderer(itemPresenter))
    }
  }
}