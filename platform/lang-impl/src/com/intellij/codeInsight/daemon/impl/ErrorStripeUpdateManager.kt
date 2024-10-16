// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx.Companion.getOpenProjects
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.function.Supplier

private val LOG = logger<ErrorStripeUpdateManager>()

@Service(Service.Level.PROJECT)
class ErrorStripeUpdateManager(private val myProject: Project) : Disposable {
  init {
    TrafficLightRendererContributor.EP_NAME.addChangeListener(Runnable {
      val psiDocumentManager = PsiDocumentManager.getInstance(myProject)
      for (fileEditor in FileEditorManager.getInstance(myProject).getAllEditors()) {
        if (fileEditor is TextEditor) {
          val editor = fileEditor.getEditor()
          val file = psiDocumentManager.getCachedPsiFile(editor.getDocument())
          repaintErrorStripePanel(editor, file)
        }
      }
    }, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ErrorStripeUpdateManager {
      return project.getService<ErrorStripeUpdateManager>(ErrorStripeUpdateManager::class.java)
    }
  }

  override fun dispose() {
  }

  @RequiresEdt
  fun repaintErrorStripePanel(editor: Editor, psiFile: PsiFile?) {
    if (!myProject.isInitialized()) {
      return
    }

    ReadAction.run<RuntimeException?>(ThrowableRunnable {
      val markup = editor.getMarkupModel() as EditorMarkupModel
      markup.setErrorPanelPopupHandler(DaemonEditorPopup(myProject, editor))
      markup.setErrorStripTooltipRendererProvider(DaemonTooltipRendererProvider(myProject, editor))
      markup.setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().errorStripeMarkMinHeight)
      if (psiFile != null) {
        setOrRefreshErrorStripeRenderer(markup, psiFile)
      }
    })
  }

  @RequiresEdt
  fun setOrRefreshErrorStripeRenderer(editorMarkupModel: EditorMarkupModel, file: PsiFile) {
    if (!editorMarkupModel.isErrorStripeVisible()) {
      return
    }
    val renderer = editorMarkupModel.getErrorStripeRenderer()
    if (renderer is TrafficLightRenderer) {
      val markupModelImpl = editorMarkupModel as EditorMarkupModelImpl
      renderer.refresh(markupModelImpl)
      markupModelImpl.repaintTrafficLightIcon()
      if (renderer.isValid()) {
        return
      }
    }

    val modality = ModalityState.defaultModalityState()
    TrafficLightRenderer.setTrafficLightOnEditor(myProject, editorMarkupModel, modality, Supplier {
      val editor = editorMarkupModel.getEditor()
      if (isEditorEligible(editor, file)) {
        return@Supplier null
      }
      createRenderer(editor, file)
    })
  }

  @RequiresBackgroundThread
  private fun createRenderer(editor: Editor, file: PsiFile?): TrafficLightRenderer {
    for (contributor in TrafficLightRendererContributor.EP_NAME.extensionList) {
      val renderer = contributor.createRenderer(editor, file)
      if (renderer != null) return renderer
    }
    return TrafficLightRenderer(myProject, editor)
  }

  private fun isEditorEligible(editor: Editor, psiFile: PsiFile): Boolean {
    return ReadAction.compute<Boolean?, RuntimeException?>(ThrowableComputable {
      val isHighlightingAvailable = DaemonCodeAnalyzer.getInstance(myProject).isHighlightingAvailable(psiFile)
      val isPsiValid = psiFile.isValid()
      val isEditorDispose = editor.isDisposed()

      LOG.debug(
        "Editor params for rendering traffic light: isEditorDispose ", isEditorDispose,
        " isPsiValid ", isPsiValid,
        " isHighlightingAvailable ", isHighlightingAvailable)
      isEditorDispose
      || !isPsiValid || !isHighlightingAvailable
    })
  }

  internal class EssentialHighlightingModeListener : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      if ("ide.highlighting.mode.essential" != value.key) {
        return
      }

      for (project in getOpenProjects()) {
        HighlightingSettingsPerFile.getInstance(project).incModificationCount()

        val allEditors = FileEditorManager.getInstance(project).getAllEditors()
        if (allEditors.size == 0) {
          return
        }

        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val stripeUpdateManager: ErrorStripeUpdateManager = getInstance(project)
        for (fileEditor in allEditors) {
          if (fileEditor is TextEditor) {
            val editor = fileEditor.getEditor()
            stripeUpdateManager.repaintErrorStripePanel(editor, psiDocumentManager.getCachedPsiFile(editor.getDocument()))
          }
        }

        // Run all checks after disabling essential highlighting
        if (!value.asBoolean()) {
          (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).restartToCompleteEssentialHighlighting()
        }
      }
    }
  }
}
