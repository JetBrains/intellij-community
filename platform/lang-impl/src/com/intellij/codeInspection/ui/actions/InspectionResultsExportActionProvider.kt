// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ui.InspectionResultsView
import com.intellij.codeInspection.ui.InspectionTree
import com.intellij.codeInspection.ui.actions.InspectionResultsExportActionProvider.Companion.LOCATION_KEY
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.nio.file.Path
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

private val LOG: Logger = Logger.getInstance(InspectionResultsExportActionProvider::class.java)

/**
 * Extension point to add actions in the inspection results export popup.
 */
abstract class InspectionResultsExportActionProvider(text: Supplier<String?>,
                                                     description: Supplier<String?>,
                                                     icon: Icon?) : InspectionViewActionBase(text, description, icon) {

  companion object {
    val EP_NAME: ExtensionPointName<InspectionResultsExportActionProvider> = ExtensionPointName.create("com.intellij.inspectionResultsExportActionProvider")
    const val LOCATION_KEY: String = "com.intellij.codeInspection.ui.actions.InspectionResultsExportActionProvider.location"
  }

  val propertyGraph: PropertyGraph = PropertyGraph()

  abstract val progressTitle: @ProgressTitle String

  override fun actionPerformed(e: AnActionEvent) {
    val view: InspectionResultsView = getView(e) ?: return

    val dialog = ExportDialog(this, view)
    if (!dialog.showAndGet()) return
    val path = dialog.path

    ProgressManager.getInstance().run(object: Backgroundable(view.project, progressTitle) {
      override fun run(indicator: ProgressIndicator) {
        try {
          runReadAction {
            if (view.currentProfile == null) throw NullPointerException("Failed to export inspection results.")
            writeResults(view.tree,
                         view.currentProfile!!,
                         view.globalInspectionContext,
                         view.project,
                         path)
          }
        }
        catch (p: ProcessCanceledException) {
          throw p
        }
        catch (t: Throwable) {
          LOG.error(t)
          invokeLater {
            Messages.showErrorDialog(view, t.message)
          }
          return
        }

        invokeLater {
          onExportSuccessful()
        }
      }
    })
  }

  /**
   * Performs the actual inspection results export.
   */
  @RequiresBackgroundThread
  abstract fun writeResults(tree: InspectionTree,
                            profile: InspectionProfileImpl,
                            globalInspectionContext: GlobalInspectionContextImpl,
                            project: Project,
                            outputPath: Path)

  @RequiresEdt
  open fun onExportSuccessful() {}

  /**
   * Additional configuration to be added in [ExportDialog].
   */
  open fun additionalSettings(): JPanel? = null

}

private class ExportDialog(private val actionProvider: InspectionResultsExportActionProvider, val view: InspectionResultsView) : DialogWrapper(view.project, true) {
  var location: String = ""

  init {
    setOKButtonText(InspectionsBundle.message("inspection.export.save.button"))
    title = InspectionsBundle.message("inspection.export.results.title")
    isResizable = false

    location = PropertiesComponent
      .getInstance(view.project)
      .getValue(LOCATION_KEY, view.project.guessProjectDir()?.path ?: "")

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
          FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(EditorBundle.message("export.to.html.select.output.directory.title")),
          view.project
        )
          .columns(COLUMNS_LARGE)
          .bind({ t -> t.text }, { t, v -> t.text = v }, ::location.toMutableProperty())
          .validationOnApply {
            if (location.isBlank()) error(InspectionsBundle.message("inspection.action.export.popup.error"))
            else null
          }
      }
      actionProvider.additionalSettings()?.let {
        row { cell(it) }
      }
    }
  }

  override fun doOKAction() {
    PropertiesComponent
      .getInstance(view.project)
      .setValue(LOCATION_KEY, location)
    super.doOKAction()
  }
}