// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<ErrorStripeUpdateManager>()
private val EP_NAME = ExtensionPointName<TrafficLightRendererContributor>("com.intellij.trafficLightRendererContributor")

@Service(Service.Level.PROJECT)
class ErrorStripeUpdateManager(private val project: Project, private val coroutineScope: CoroutineScope) {
  init {
    EP_NAME.addChangeListener(coroutineScope) {
      launchRepaintErrorStripePanel(
        editors = FileEditorManager.getInstance(project).getAllEditors().mapNotNull { (it as? TextEditor)?.editor },
        getFileAsCached = true,
      )
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ErrorStripeUpdateManager = project.service()
  }

  @ApiStatus.Internal
  fun launchRepaintErrorStripePanel(editors: List<Editor>, getFileAsCached: Boolean) {
    if (editors.isEmpty() || !project.isInitialized()) {
      return
    }

    val models = editors.mapNotNull { it.markupModel as? EditorMarkupModel }
    if (models.isEmpty()) {
      return
    }

    coroutineScope.launch {
      callRepaintErrorStripePanel(models, getFileAsCached)
    }
  }

  @JvmName("launchRepaintErrorStripePanel")
  internal fun launchRepaintErrorStripePanel(model: EditorMarkupModel, file: PsiFile?) {
    coroutineScope.launch {
      asyncRepaintErrorStripePanel(model, file)
    }
  }

  private suspend fun callRepaintErrorStripePanel(
    models: List<EditorMarkupModel>,
    getFileAsCached: Boolean,
  ) {
    val psiDocumentManager = project.serviceAsync<PsiDocumentManager>()
    for (model in models) {
      val editor = model.editor.takeIf { !it.isDisposed } ?: continue
      val file = readAction {
        val document = editor.getDocument()
        if (getFileAsCached) psiDocumentManager.getCachedPsiFile(document) else psiDocumentManager.getPsiFile(document)
      }
      asyncRepaintErrorStripePanel(model, file)
    }
  }

  @ApiStatus.Internal
  fun launchRepaintErrorStripePanel(editor: Editor, file: PsiFile?) {
    val model = editor.markupModel as? EditorMarkupModel ?: return
    coroutineScope.launch {
      asyncRepaintErrorStripePanel(model, file)
    }
  }

  @Deprecated("Use launchRepaintErrorStripePanel(List<Editor>) or launchRepaintErrorStripePanel(Editor, PsiFile) instead")
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
      if (psiFile != null && psiFile.isValid) {
        setOrRefreshErrorStripeRenderer(markup, psiFile)
      }
    }
  }

  internal suspend fun asyncRepaintErrorStripePanel(markup: EditorMarkupModel, psiFile: PsiFile?) {
    if (!project.isInitialized()) {
      return
    }

    val errorStripeMarkMinHeight = serviceAsync<DaemonCodeAnalyzerSettings>().errorStripeMarkMinHeight
    val setNeeded = withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        markup.setErrorPanelPopupHandler(DaemonEditorPopup(project, markup.editor))
        markup.setErrorStripTooltipRendererProvider(DaemonTooltipRendererProvider(project, markup.editor))
        markup.setMinMarkHeight(errorStripeMarkMinHeight)
        if (psiFile == null) {
          false
        }
        else {
          refreshErrorStripeRenderer(markup)
        }
      }
    }

    if (setNeeded) {
      setTrafficLightOnEditorIfNeeded(markup, psiFile!!, project)
    }
  }

  @RequiresEdt
  @JvmName("setOrRefreshErrorStripeRenderer")
  internal fun setOrRefreshErrorStripeRenderer(model: EditorMarkupModel, file: PsiFile) {
    if (refreshErrorStripeRenderer(model)) {
      coroutineScope.launch(ModalityState.defaultModalityState().asContextElement()) {
        setTrafficLightOnEditorIfNeeded(model, file, project)
      }
    }
  }
}

/**
 * Returns true if [TrafficLightRenderer] should be recreated via [setTrafficLightOnEditorIfNeeded]
 */
private fun refreshErrorStripeRenderer(editorMarkupModel: EditorMarkupModel): Boolean {
  if (!editorMarkupModel.isErrorStripeVisible()) {
    return false
  }

  val renderer = editorMarkupModel.getErrorStripeRenderer()
  if (renderer is TrafficLightRenderer) {
    renderer.refresh(editorMarkupModel)
    (editorMarkupModel as EditorMarkupModelImpl).repaintTrafficLightIcon()
    return !renderer.isValid()
  }
  else {
    return true
  }
}

private suspend fun setTrafficLightOnEditorIfNeeded(model: EditorMarkupModel, file: PsiFile, project: Project) {
  val editor = model.getEditor()
  if (isEditorEligible(editor, file, project)) {
    return
  }

  var rendererWasSet = false
  val renderer = createRenderer(editor, file, project)
  try {
    withContext(Dispatchers.EDT) {
      val editor = model.getEditor()
      if (project.isDisposed() || editor.isDisposed()) {
        LOG.debug { "Traffic light won't be set to editor: project dispose=${project.isDisposed()}, editor dispose=${editor.isDisposed()}" }
        // would be registered in setErrorStripeRenderer() below
        Disposer.dispose(renderer)
      }
      else {
        model.setErrorStripeRenderer(renderer)
        rendererWasSet = true
      }
    }
  }
  finally {
    if (!rendererWasSet) {
      Disposer.dispose(renderer)
    }
  }
}

private suspend fun createRenderer(editor: Editor, file: PsiFile, project: Project): TrafficLightRenderer {
  for (contributor in EP_NAME.extensionList) {
    contributor.createRenderer(editor, file)?.let {
      return it
    }
  }
  return TrafficLightRenderer.createTrafficLightRenderer(editor, file, project)
}

private suspend fun isEditorEligible(editor: Editor, psiFile: PsiFile, project: Project): Boolean {
  val daemonCodeAnalyzer = project.serviceAsync<DaemonCodeAnalyzer>()
  return readAction {
    val isHighlightingAvailable = daemonCodeAnalyzer.isHighlightingAvailable(psiFile)
    val isPsiValid = psiFile.isValid()
    val isEditorDispose = editor.isDisposed()
    LOG.debug {
      "Editor params for rendering traffic light " +
      "(isEditorDispose=$isEditorDispose, isPsiValid=$isPsiValid, isHighlightingAvailable=$isHighlightingAvailable"
    }
    isEditorDispose || !isPsiValid || !isHighlightingAvailable
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

      ErrorStripeUpdateManager.getInstance(project).launchRepaintErrorStripePanel(
        editors = allEditors.mapNotNull { (it as? TextEditor)?.editor },
        getFileAsCached = true,
      )

      // Run all checks after disabling essential highlighting
      if (!value.asBoolean()) {
        (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).requestRestartToCompleteEssentialHighlighting()
      }
    }
  }
}
