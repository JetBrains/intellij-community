// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.export.InspectionTreeHtmlWriter
import com.intellij.codeInspection.ui.InspectionResultsView
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import java.nio.file.Path

class ExportToHTMLAction : InspectionResultsExportActionBase(InspectionsBundle.messagePointer("inspection.action.export.html.title"),
                                                             InspectionsBundle.messagePointer("inspection.action.export.html.title"),
                                                             AllIcons.FileTypes.Html) {
  override val progressTitle: String = InspectionsBundle.message("inspection.generating.html.progress.title")

  val openProperty = propertyGraph.property(false)
  val open by openProperty
  var outputPath: Path? = null

  override fun writeResults(view: InspectionResultsView, outputPath: Path) {
    InspectionTreeHtmlWriter(view, outputPath)
    this.outputPath = outputPath
  }

  override fun onExportSuccessful() {
    if (open) {
      val path = outputPath ?: return
      BrowserUtil.browse(path.resolve("index.html").toFile())
    }
  }

  override fun Panel.additionalSettings() {
    row {
      checkBox(InspectionsBundle.message("inspection.export.open.option")).bindSelected(openProperty)
    }
  }
}