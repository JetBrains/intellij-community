// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ui.InspectionResultsView
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.nio.file.Path
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

val LOG = Logger.getInstance(InspectionResultsExportActionProvider::class.java)

/**
 * Extension point to add actions in the inspections results export popup.
 */
abstract class InspectionResultsExportActionProvider(text: Supplier<String?>,
                                                     description: Supplier<String?>,
                                                     icon: Icon?) : InspectionViewActionBase(text, description, icon) {

  companion object {
    val EP_NAME: ExtensionPointName<InspectionResultsExportActionProvider> = ExtensionPointName.create("com.intellij.inspectionResultsExportActionProvider")
  }

  val propertyGraph = PropertyGraph()

  abstract val progressTitle: @ProgressTitle String

  override fun actionPerformed(e: AnActionEvent) {
    val view: InspectionResultsView = getView(e) ?: return

    val dialog = ExportDialog(view)
    if (!dialog.showAndGet()) return
    val path = dialog.path

    ApplicationManager.getApplication().runReadAction {
      try {
        writeResults(view, path)
      }
      catch (e: Exception) {
        LOG.error(e)
        ApplicationManager.getApplication().invokeLater {
          Messages.showErrorDialog(view, e.message)
        }
      }
    }

    onExportSuccessful()
  }

  /**
   * Performs the actual inspection results export.
   */
  @RequiresBackgroundThread
  abstract fun writeResults(view: InspectionResultsView, outputPath: Path)

  open fun onExportSuccessful() {}

  /**
   * Additional configuration to be added in [ExportDialog].
   */
  open fun additionalSettings(): JPanel? = null

  inner class ExportDialog(val view: InspectionResultsView) : DialogWrapper(view.project, true) {
    val locationProperty = propertyGraph.property("")
    val location by locationProperty

    init {
      setOKButtonText(InspectionsBundle.message("inspection.export.save.button"))
      title = InspectionsBundle.message("inspection.export.results.title")
      isResizable = false
      init()
    }

    val path: Path
      get() = Path.of(location)

    override fun createCenterPanel(): JComponent {
      return panel {
        row {
          @Suppress("DialogTitleCapitalization")
          label(view.viewTitle)
            .bold()
        }
          .bottomGap(BottomGap.SMALL)
        row(EditorBundle.message("export.to.html.output.directory.label")) {
          textFieldWithBrowseButton(
            EditorBundle.message("export.to.html.select.output.directory.title"),
            view.project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
          )
            .columns(COLUMNS_LARGE)
            .bindText(locationProperty)
            .validationOnApply {
              if (location.isBlank()) error(InspectionsBundle.message("inspection.action.export.popup.error"))
              else null
            }
        }
        additionalSettings()?.let {
          row { cell(it) }
        }
      }
    }
  }
}