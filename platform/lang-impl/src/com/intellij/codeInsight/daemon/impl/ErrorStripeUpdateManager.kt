// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

private val LOG = logger<ErrorStripeUpdateManager>()
private val EP_NAME = ExtensionPointName<TrafficLightRendererContributor>("com.intellij.trafficLightRendererContributor")

@Service(Service.Level.PROJECT)
class ErrorStripeUpdateManager(private val project: Project, coroutineScope: CoroutineScope) {
  init {
    EP_NAME.addChangeListener(coroutineScope) {
      val psiDocumentManager = PsiDocumentManager.getInstance(project)
      for (fileEditor in FileEditorManager.getInstance(project).getAllEditors()) {
        if (fileEditor is TextEditor) {
          val editor = fileEditor.getEditor()
          val file = psiDocumentManager.getCachedPsiFile(editor.getDocument())
          repaintErrorStripePanel(editor, file)
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ErrorStripeUpdateManager = project.service()
  }

  @RequiresEdt
  fun repaintErrorStripePanel(editor: Editor, psiFile: PsiFile?) {
    if (!project.isInitialized()) {
      return
    }

    ApplicationManager.getApplication().runReadAction {
      val markup = editor.getMarkupModel() as EditorMarkupModel
      markup.setErrorPanelPopupHandler(DaemonEditorPopup(project, editor))
      markup.setErrorStripTooltipRendererProvider(DaemonTooltipRendererProvider(project, editor))
      markup.setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().errorStripeMarkMinHeight)
      if (psiFile != null) {
        setOrRefreshErrorStripeRenderer(markup, psiFile)
      }
    }
  }

  @ApiStatus.Internal
  suspend fun asyncRepaintErrorStripePanel(markup: EditorMarkupModel, psiFile: PsiFile?) {
    if (!project.isInitialized()) {
      return
    }

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        markup.setErrorPanelPopupHandler(DaemonEditorPopup(project, markup.editor))
        markup.setErrorStripTooltipRendererProvider(DaemonTooltipRendererProvider(project, markup.editor))
        markup.setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().errorStripeMarkMinHeight)
        if (psiFile != null) {
          setOrRefreshErrorStripeRenderer(markup, psiFile)
        }
      }
    }
  }

  @RequiresEdt
  @JvmName("setOrRefreshErrorStripeRenderer")
  internal fun setOrRefreshErrorStripeRenderer(editorMarkupModel: EditorMarkupModel, file: PsiFile) {
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
    TrafficLightRenderer.setTrafficLightOnEditor(project, editorMarkupModel, modality, Supplier {
      val editor = editorMarkupModel.getEditor()
      if (isEditorEligible(editor, file)) {
        return@Supplier null
      }
      createRenderer(editor, file)
    })
  }

  @RequiresBackgroundThread
  private fun createRenderer(editor: Editor, file: PsiFile?): TrafficLightRenderer {
    for (contributor in EP_NAME.extensionList) {
      contributor.createRenderer(editor, file)?.let {
        return it
      }
    }
    return TrafficLightRenderer(project, editor)
  }

  private fun isEditorEligible(editor: Editor, psiFile: PsiFile): Boolean {
    return ApplicationManager.getApplication().runReadAction(ThrowableComputable {
      val isHighlightingAvailable = DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(psiFile)
      val isPsiValid = psiFile.isValid()
      val isEditorDispose = editor.isDisposed()

      if (LOG.isDebugEnabled) {
        LOG.debug("Editor params for rendering traffic light " +
                  "(isEditorDispose=$isEditorDispose, isPsiValid=$isPsiValid, isHighlightingAvailable=$isHighlightingAvailable")
      }
      isEditorDispose || !isPsiValid || !isHighlightingAvailable
    })
  }
}

private class EssentialHighlightingModeListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if ("ide.highlighting.mode.essential" != value.key) {
      return
    }

    for (project in ProjectManagerEx.getOpenProjects()) {
      HighlightingSettingsPerFile.getInstance(project).incModificationCount()

      val allEditors = FileEditorManager.getInstance(project).getAllEditors()
      if (allEditors.isEmpty()) {
        return
      }

      val psiDocumentManager = PsiDocumentManager.getInstance(project)
      val stripeUpdateManager = ErrorStripeUpdateManager.getInstance(project)
      for (fileEditor in allEditors) {
        if (fileEditor is TextEditor) {
          val editor = fileEditor.getEditor()
          stripeUpdateManager.repaintErrorStripePanel(editor = editor, psiFile = psiDocumentManager.getCachedPsiFile(editor.getDocument()))
        }
      }

      // Run all checks after disabling essential highlighting
      if (!value.asBoolean()) {
        (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).restartToCompleteEssentialHighlighting()
      }
    }
  }
}
