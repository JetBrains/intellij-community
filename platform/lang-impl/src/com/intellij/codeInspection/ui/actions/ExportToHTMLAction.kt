// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.export.InspectionTreeHtmlWriter
import com.intellij.codeInspection.ui.InspectionTree
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
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

  override fun writeResults(tree: InspectionTree,
                            profile: InspectionProfileImpl,
                            globalInspectionContext: GlobalInspectionContextImpl,
                            project: Project,
                            outputPath: Path) {
    InspectionTreeHtmlWriter(tree, profile, globalInspectionContext.refManager, outputPath)
  }

  override fun onExportSuccessful(data: UserDataHolderEx) {
    if (data.getUserData(openKey) == true) {
      val path = data.getUserData(ExportDialog.LOCATION_KEY) ?: return
      BrowserUtil.browse(Path.of(path, "index.html"))
    }
  }

  override fun additionalSettings(data: UserDataHolderEx): JPanel {
    return panel {
      row {
        checkBox(InspectionsBundle.message("inspection.export.open.option")).applyToComponent {
          addChangeListener { data.putUserData(openKey, isSelected) }
        }
      }
    }
  }
}

private val openKey: Key<Boolean> = Key.create("export.to.html.action.open")