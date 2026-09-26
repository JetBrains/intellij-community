// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.ui.speedSearch.FilteringListModel
import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultListSelectionModel
import javax.swing.JList
import javax.swing.plaf.ListUI

private val logger = fileLogger()

@ApiStatus.Internal
interface BulkUpdateListSelectionModel {
  val isBulkUpdateInProgress: Boolean

  fun enterBulkUpdate()

  fun exitBulkUpdate()
}

@ApiStatus.Internal
interface BulkUpdateListUi {
  fun beforeBulkListUpdate()

  fun afterBulkListUpdate()
}

/**
 * Perform [FilteringListModel.refilter] command without firing events on every incremental change.
 * This is a workaround for performance bottleneck for meditum-to-big lists.
 * Otherwise, the list's preferred size is being calculated multiple times during [com.intellij.ui.speedSearch.FilteringListModel.commit].
 *
 * WARNING: this operation will NOT track the current selection
 */
@ApiStatus.Experimental
fun refilterListModelInBulk(list: JList<*>) {
  val selectionModel = list.selectionModel
  val filteringModel = list.model as? FilteringListModel<*>
  if (filteringModel == null) {
    logger.error("List model is not a FilteringListModel: ${list.model}")
    return
  }

  if (selectionModel is BulkUpdateListSelectionModel) {
    selectionModel.enterBulkUpdate()
    try {
      filteringModel.refilter()
    }
    finally {
      selectionModel.exitBulkUpdate()
    }
  }
  else {
    filteringModel.refilter()
  }
}

internal class BulkDefaultListSelectionModel(private val list: JList<*>) : DefaultListSelectionModel(), BulkUpdateListSelectionModel {
  private var myBulkUpdateDepth = 0

  override val isBulkUpdateInProgress: Boolean
    get() = myBulkUpdateDepth > 0

  override fun enterBulkUpdate() {
    myBulkUpdateDepth++

    if (myBulkUpdateDepth == 1) {
      val listUI: ListUI? = list.getUI()
      if (listUI is BulkUpdateListUi) {
        listUI.beforeBulkListUpdate()
      }
    }
  }

  override fun exitBulkUpdate() {
    myBulkUpdateDepth--

    if (myBulkUpdateDepth == 0) {
      val listUI: ListUI? = list.getUI()
      if (listUI is BulkUpdateListUi) {
        listUI.afterBulkListUpdate()
      }
    }
  }
}
