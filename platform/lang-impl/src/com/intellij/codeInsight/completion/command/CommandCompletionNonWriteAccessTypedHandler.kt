// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.command.configuration.ApplicationCommandCompletionService
import com.intellij.codeInsight.editorActions.NonWriteAccessTypedHandler
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.*
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
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Handles non-write access typed events for command-based code completion functionality.
 * This class extends the `NonWriteAccessTypedHandler` to provide custom behavior for handling typed characters
 * and triggering command completions without modifying the editor's document directly.
 *
 */
@ApiStatus.Internal
internal class CommandCompletionNonWriteAccessTypedHandler : NonWriteAccessTypedHandler {
  override fun isApplicable(editor: Editor, charTyped: Char, dataContext: DataContext): Boolean {
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return false
    val project = editor.project ?: return false
    val commandCompletionService = project.getService(CommandCompletionService::class.java)
    if (commandCompletionService == null) return false
    var targetFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) ?: return false
    var offset = editor.caretModel.offset
    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    val injectedElement = injectedLanguageManager.findInjectedElementAt(targetFile, offset)
    if (injectedElement != null) {
      targetFile = injectedElement.containingFile
      offset = (targetFile.fileDocument as? DocumentWindow)?.hostToInjected(offset) ?: return false
    }
    val dumbService = DumbService.getInstance(project)
    val commandCompletionFactory = commandCompletionService.getFactory(targetFile.language)
    if (commandCompletionFactory == null) return false
    if (!dumbService.isUsableInCurrentContext(commandCompletionFactory)) return false
    if (StringUtil.isJavaIdentifierPart(editor.document.immutableCharSequence[offset])) return false
    return commandCompletionFactory.suffix() == charTyped &&
           commandCompletionFactory.isApplicable(targetFile, offset)
  }

  override fun handle(editor: Editor, charTyped: Char, dataContext: DataContext) {
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return
    val accessCommandCompletionService = editor.project?.getService(NonWriteAccessCommandCompletionService::class.java)
    if (accessCommandCompletionService == null) return
    accessCommandCompletionService.insertNewEditor(editor)
  }
}

internal val INSTALLED_EDITOR = Key.create<Inlay<ComponentInlayRenderer<LanguageTextField>>>("completion.command.non.writable.editor")
internal val ORIGINAL_EDITOR = Key.create<Pair<Editor, Int>>("completion.command.original.editor")

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
internal class NonWriteAccessCommandCompletionService(
  val coroutineScope: CoroutineScope,
) {

  fun insertNewEditor(editor: Editor) {
    if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return
    if (editor !is EditorImpl) return
    val project = editor.project ?: return
    val offset = editor.caretModel.offset
    val inlayProperties = InlayProperties().relatesToPrecedingText(true).showAbove(false).showWhenFolded(false)
    val textField = LanguageTextField(FileTypes.PLAIN_TEXT.language, editor.project, "", true)
    val height = ((editor.charHeight * 2 * 1.2) / JBUIScale.scale(1.0F)).toInt() + 10
    val width = editor.lineHeight * 6
    val size = JBUI.size(width, height)
    textField.maximumSize = size
    textField.minimumSize = size
    textField.preferredSize = size
    val componentInlay: Inlay<ComponentInlayRenderer<LanguageTextField>>? = editor.addComponentInlay(offset, inlayProperties, ComponentInlayRenderer(textField, ComponentInlayAlignment.INLINE_COMPONENT))
    if (componentInlay == null) return
    IdeFocusManager.getInstance(editor.project).requestFocus(textField, true)
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
      if (activeLookup == null || activeLookup !is Disposable) {
        Disposer.dispose(componentInlay)
        return@run
      }
      Disposer.register(activeLookup, componentInlay)
    }
  }
}