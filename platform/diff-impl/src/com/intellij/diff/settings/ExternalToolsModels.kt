// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.settings

import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolConfiguration
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolGroup
import com.intellij.openapi.diff.DiffBundle
import com.intellij.ui.CheckedTreeNode
import com.intellij.util.ui.ListTableModel
import javax.swing.tree.DefaultTreeModel

internal class ExternalToolsModels {
  val treeModel = DefaultTreeModel(CheckedTreeNode())

  val tableModel = ListTableModel<ExternalToolConfiguration>(
    ExternalToolsTablePanel.FileTypeColumn(this),
    ExternalToolsTablePanel.ExternalToolColumn(ExternalToolGroup.DIFF_TOOL,
                                               treeModel,
                                               DiffBundle.message("settings.external.diff.table.difftool.column")),
    ExternalToolsTablePanel.ExternalToolColumn(ExternalToolGroup.MERGE_TOOL,
                                               treeModel,
                                               DiffBundle.message("settings.external.diff.table.mergetool.column"))
  )
}