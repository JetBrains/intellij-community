// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.export.InspectionTreeHtmlWriter
import com.intellij.codeInspection.ui.InspectionTree
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.function.Supplier
import javax.swing.JPanel

@ApiStatus.Internal
@Suppress("ComponentNotRegistered")
class ExportToHTMLAction : InspectionResultsExportActionProvider(Supplier { "HTML" },
                                                                 InspectionsBundle.messagePointer("inspection.action.export.html.description"),
                                                                 AllIcons.FileTypes.Html) {
  override val progressTitle: String = InspectionsBundle.message("inspection.generating.html.progress.title")

  val openProperty: GraphProperty<Boolean> = propertyGraph.property(false)
  val open: Boolean by openProperty
  var outputPath: Path? = null

  override fun writeResults(tree: InspectionTree,
                            profile: InspectionProfileImpl,
                            globalInspectionContext: GlobalInspectionContextImpl,
                            project: Project,
                            outputPath: Path) {
    this.outputPath = outputPath
    InspectionTreeHtmlWriter(tree, profile, globalInspectionContext.refManager, outputPath)
  }

  override fun onExportSuccessful() {
    if (open) {
      val path = outputPath ?: return
      BrowserUtil.browse(path.resolve("index.html").toFile())
    }
  }

  override fun additionalSettings(): JPanel {
    return panel {
      row {
        checkBox(InspectionsBundle.message("inspection.export.open.option")).bindSelected(openProperty)
      }
    }
  }
}