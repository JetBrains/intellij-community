// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.java.frontback.impl.JavaFrontbackBundle
import com.intellij.psi.codeStyle.PackageEntryTable
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.Dimension

internal fun doCreatePackagesPanel(packageTable: JBTable, packageList: PackageEntryTable) = panel {
  val panel = ToolbarDecorator.createDecorator(packageTable)
    .setAddAction {
      PackagePanel.addPackageToPackages(packageTable, packageList)
    }
    .setRemoveAction {
      PackagePanel.removeEntryFromPackages(packageTable, packageList)
    }
    .disableUpDownActions()
    .setPreferredSize(Dimension(-1, JBUI.scale(180)))
    .createPanel()

  row {
    cell(panel)
      .align(Align.FILL)
      .label(JavaFrontbackBundle.message("title.packages.to.use.import.with"), LabelPosition.TOP)
  }
    .resizableRow()
}