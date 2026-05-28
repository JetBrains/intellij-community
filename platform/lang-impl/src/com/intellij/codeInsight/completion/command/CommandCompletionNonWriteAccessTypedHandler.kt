// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.command.configuration.ApplicationCommandCompletionService
import com.intellij.codeInsight.completion.command.configuration.CommandCompletionSettingsService
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.idea.AppMode
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ComponentInlayAlignment
import com.intellij.openapi.editor.ComponentInlayRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.NonWriteAccessTypedHandler
import com.intellij.openapi.editor.addComponentInlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.LanguageTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles non-write access typed events for command-based code completion functionality.
 * This class extends the `NonWriteAccessTypedHandler` to provide custom behavior for handling typed characters
 * and triggering command completions without modifying the editor's document directly.
 *
 */
internal class CommandCompletionNonWriteAccessTypedHandler : NonWriteAccessTypedHandler {
  override fun isApplicable(editor: Editor, charTyped: Char, dataContext: DataContext): Boolean {
    if (!isGenerallyApplicable()) return false
    val project = editor.project ?: return false

    val offset = editor.caretModel.offset
    val document = editor.document
    if (StringUtil.isJavaIdentifierPart(document.immutableCharSequence[offset])) return false

    val targetFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return false

    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    val injectedElement = injectedLanguageManager.findInjectedElementAt(targetFile, offset)
    if (injectedElement != null) return false

    val commandCompletionFactory = project.service<CommandCompletionService>().getFactory(targetFile.language) ?: return false
    if (!DumbService.getInstance(project).isUsableInCurrentContext(commandCompletionFactory)) return false

    return commandCompletionFactory.suffix() == charTyped &&
           commandCompletionFactory.isApplicable(targetFile, offset)
  }

  override fun handle(editor: Editor, charTyped: Char, dataContext: DataContext) {
    if (!isGenerallyApplicable()) return
    val accessCommandCompletionService = editor.project?.service<NonWriteAccessCommandCompletionService>() ?: return

    accessCommandCompletionService.insertNewEditor(editor)
  }

  private fun isGenerallyApplicable(): Boolean {
    val service = CommandCompletionSettingsService.getInstance()
    if (!service.commandCompletionEnabled()) return false
    if (!service.readOnlyEnabled()) return false
    if (AppMode.isRemoteDevHost()) return false
    if (PlatformUtils.isJetBrainsClient()) return false
    return true
  }
}

internal val INSTALLED_EDITOR = Key.create<Inlay<ComponentInlayRenderer<LanguageTextField>>>("completion.command.non.writable.editor")
internal val ORIGINAL_EDITOR = Key.create<Pair<Editor, Int>>("completion.command.original.editor")

@Service(Service.Level.PROJECT)
internal class NonWriteAccessCommandCompletionService(
  private val coroutineScope: CoroutineScope,
) {

  fun insertNewEditor(editor: Editor) {
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return
    if (editor !is EditorImpl) return
    val project = editor.project ?: return

    val textField = LanguageTextField(FileTypes.PLAIN_TEXT.language, editor.project, "", true).apply {
      val height = ((editor.charHeight * 2 * 1.2) / JBUIScale.scale(1.0F)).toInt() + 10
      val width = editor.lineHeight * 6
      val size = JBUI.size(width, height)
      this.maximumSize = size
      this.minimumSize = size
      this.preferredSize = size
    }

    val inlayProperties = InlayProperties().relatesToPrecedingText(true).showAbove(false).showWhenFolded(false)
    val offset = editor.caretModel.offset
    val componentInlay = editor.addComponentInlay(
      offset,
      inlayProperties,
      ComponentInlayRenderer(textField, ComponentInlayAlignment.INLINE_COMPONENT)
    ) ?: return

    IdeFocusManager.getInstance(project).requestFocus(textField, true)

    editor.putUserData(INSTALLED_EDITOR, componentInlay)
    Disposer.register(editor.disposable, componentInlay)
    textField.setDisposedWith(editor.disposable)

    val inlayedEditor = textField.getEditor(true) ?: return
    inlayedEditor.putUserData(ORIGINAL_EDITOR, Pair(editor, offset))

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runCompletion(project, inlayedEditor, componentInlay)
    }
    else {
      coroutineScope.launch(Dispatchers.EDT) {
        runCompletion(project, inlayedEditor, componentInlay)
      }
    }
  }

  private fun runCompletion(
    project: Project,
    inlayedEditor: Editor,
    componentInlay: Inlay<ComponentInlayRenderer<LanguageTextField>>,
  ) {
    WriteIntentReadAction.run {
      val codeCompletionHandlerBase = CodeCompletionHandlerBase(CompletionType.BASIC, true, false, true)
      codeCompletionHandlerBase.invokeCompletion(project, inlayedEditor, 1)
      val activeLookup = LookupManager.getActiveLookup(inlayedEditor)
      if (activeLookup !is Disposable) {
        Disposer.dispose(componentInlay)
        return@run
      }
      Disposer.register(activeLookup, componentInlay)
    }
  }
}