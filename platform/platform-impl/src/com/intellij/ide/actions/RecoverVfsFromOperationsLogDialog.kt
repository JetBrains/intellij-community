// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils
import com.intellij.ui.SortedComboBoxModel
import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.panel
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.Action

private var Action.text
  get() = getValue(Action.NAME)
  set(value) = putValue(Action.NAME, value)

class RecoverVfsFromOperationsLogDialog(
  project: Project?,
  canRestart: Boolean,
  recoveryPoints: Sequence<VfsRecoveryUtils.RecoveryPoint>
) : DialogWrapper(project) {
  private class RecoveryPointWrapper(val rp: VfsRecoveryUtils.RecoveryPoint) {
    override fun toString(): String {
      return dateFormat.format(Date(rp.timestamp))
    }
    companion object {
      private val dateFormat = SimpleDateFormat("HH:mm:ss dd-MM-yyyy")
    }
  }

  private val comboboxModel = SortedComboBoxModel(
    recoveryPoints.take(10).toList().map(::RecoveryPointWrapper),
    Comparator { o1, o2 ->
      o1.rp.timestamp.compareTo(o2.rp.timestamp) * -1
    }
  )
  val selectedRecoveryPoint: VfsRecoveryUtils.RecoveryPoint? get() = comboboxModel.selectedItem?.rp

  init {
    title = IdeBundle.message("dialog.title.recover.vfs.from.logs")
    isResizable = false
    init()

    okAction.text = when {
      canRestart -> IdeBundle.message("button.recover.vfs.from.logs.and.restart")
      else -> IdeBundle.message("button.recover.vfs.from.logs.and.exit")
    }

    cancelAction.text = IdeBundle.message("button.cancel.without.mnemonic")

    if (comboboxModel.items.isNotEmpty()) {
      comboboxModel.selectedItem = comboboxModel.items[0]
    }
  }

  override fun createCenterPanel(): DialogPanel = panel {
    row {
      text(IdeBundle.message("dialog.message.recover.vfs.from.operations.log"), maxLineLength = DEFAULT_COMMENT_WIDTH)
    }
    row {
      text(IdeBundle.message("dialog.choose.recovery.point"))
      comboBox(comboboxModel)
    }
  }
}