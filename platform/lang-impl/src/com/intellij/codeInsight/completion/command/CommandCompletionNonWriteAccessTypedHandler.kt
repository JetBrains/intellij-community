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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.editor.ComponentInlayAlignment
import com.intellij.openapi.editor.ComponentInlayRenderer
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.NonWriteAccessTypedHandler
import com.intellij.openapi.editor.addComponentInlay
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.LanguageTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Handles non-write access typed events for command-based code completion functionality.
 * This class extends the `NonWriteAccessTypedHandler` to provide custom behavior for handling typed characters
 * and triggering command completions without modifying the editor's document directly.
 *
 */
internal class CommandCompletionNonWriteAccessTypedHandler : NonWriteAccessTypedHandler {
  override fun isApplicable(editor: Editor, charTyped: Char, dataContext: DataContext): Boolean {
    return isGenerallyApplicable() && NonWriteAccessCommandCompletionSupport.Backend.isApplicable(editor, charTyped)
  }

  override fun handle(editor: Editor, charTyped: Char, dataContext: DataContext) {
    if (!isGenerallyApplicable()) return
    if (AppMode.isRemoteDevHost()) {
      // the command completion inlay is shown on the frontend (see FrontendCommandCompletionNonWriteAccessTypedHandler
      // in intellij.platform.completion.frontend.split); consuming the char here only suppresses the backend
      // "file is read-only" hint that would otherwise be mirrored to the client on top of that inlay
      return
    }
    val accessCommandCompletionService = editor.project?.service<NonWriteAccessCommandCompletionService>() ?: return

    accessCommandCompletionService.insertNewEditor(editor)
  }

  private fun isGenerallyApplicable(): Boolean {
    if (!NonWriteAccessCommandCompletionSupport.isEnabled()) return false
    if (PlatformUtils.isJetBrainsClient()) return false
    return true
  }
}

internal val INSTALLED_EDITOR = Key.create<Inlay<ComponentInlayRenderer<LanguageTextField>>>("completion.command.non.writable.editor")
internal val ORIGINAL_EDITOR = Key.create<Pair<Editor, Int>>("completion.command.original.editor")

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class NonWriteAccessCommandCompletionService(
  private val coroutineScope: CoroutineScope,
) {

  fun insertNewEditor(editor: Editor) {
    insertNewEditorImpl(editor, {}, null)
  }

  fun insertNewEditor(
    editor: Editor,
    configureDocument: (Document) -> Unit,
    prepareCompletion: suspend (Editor) -> Boolean,
  ) {
    insertNewEditorImpl(editor, configureDocument, prepareCompletion)
  }

  private fun insertNewEditorImpl(
    editor: Editor,
    configureDocument: (Document) -> Unit,
    prepareCompletion: (suspend (Editor) -> Boolean)?,
  ) {
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
    configureDocument(textField.document)

    val inlayProperties = InlayProperties().relatesToPrecedingText(true).showAbove(false).showWhenFolded(false)
    val offset = editor.caretModel.offset
    val componentInlay = editor.addComponentInlay(
      offset,
      inlayProperties,
      ComponentInlayRenderer(textField, ComponentInlayAlignment.INLINE_COMPONENT)
    ) ?: return

    IdeFocusManager.getInstance(project).requestFocus(textField, true)

    editor.putUserData(INSTALLED_EDITOR, componentInlay)
    val editorLifetimeDisposable = Disposer.newDisposable("command completion non-writable inlay")
    EditorUtil.disposeWithEditor(editor, editorLifetimeDisposable)
    Disposer.register(editorLifetimeDisposable, componentInlay)
    textField.setDisposedWith(editorLifetimeDisposable)

    val inlayedEditor = textField.getEditor(true) ?: return
    inlayedEditor.putUserData(ORIGINAL_EDITOR, Pair(editor, offset))

    if (prepareCompletion != null) {
      val job = coroutineScope.launch(Dispatchers.Default) {
        val isPrepared = logger<NonWriteAccessCommandCompletionService>().runAndLogException {
          prepareCompletion(inlayedEditor)
        } == true

        withContext(Dispatchers.EDT) {
          if (!isPrepared || inlayedEditor.isDisposed) {
            Disposer.dispose(componentInlay)
          }
          else {
            runCompletion(project, inlayedEditor, componentInlay)
          }
        }
      }
      Disposer.register(componentInlay) {
        job.cancel(CancellationException("Command completion editor disposed"))
      }
    }
    else if (ApplicationManager.getApplication().isUnitTestMode()) {
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

@ApiStatus.Internal
object NonWriteAccessCommandCompletionSupport {
  private val REMOTE_ORIGINAL_EDITOR = Key.create<Pair<EditorId, Int>>("completion.command.non.writable.remote.original.editor")

  // ---------------------------------------------------------------------------------------------------------
  // Called on BOTH sides (and in the local monolith): settings checks and user-data reads used by the common
  // command completion pipeline (typed handlers, CommandCompletionProvider, listener, insert handler).
  // ---------------------------------------------------------------------------------------------------------

  fun isEnabled(): Boolean {
    val service = CommandCompletionSettingsService.getInstance()
    return service.commandCompletionEnabled() && service.readOnlyEnabled()
  }

  internal fun originalEditor(editor: Editor): Pair<Editor, Int>? {
    editor.getUserData(ORIGINAL_EDITOR)?.let { return it }
    val (originalEditorId, offset) = editor.document.getUserData(REMOTE_ORIGINAL_EDITOR) ?: return null
    val originalEditor = originalEditorId.findEditorOrNull() ?: return null
    return originalEditor to offset
  }

  /**
   * Called where the full PSI lives — on the remote-dev BACKEND (host) or in the local monolith:
   * from [CommandCompletionNonWriteAccessTypedHandler], the RPC implementation and the
   * `VirtualFileCustomDataProvider` in `intellij.platform.completion.backend.split`.
   */
  object Backend {
    fun isApplicable(editor: Editor, charTyped: Char): Boolean =
      findApplicableFactory(editor, editor.caretModel.offset, charTyped) != null

    fun isApplicable(editor: Editor, offset: Int, charTyped: Char): Boolean =
      findApplicableFactory(editor, offset, charTyped) != null

    /**
     * The command completion trigger suffix for the language of the file,
     * or `null` when there is no factory for the language.
     *
     * Deliberately independent of [isEnabled]: the result is stored per file on the frontend
     * (see [Frontend.setSuffix]), while the settings are re-checked on every keystroke.
     */
    fun suffixFor(psiFile: PsiFile): Char? =
      psiFile.project.service<CommandCompletionService>().getFactory(psiFile.language)?.suffix()

    /** Marks the dummy backend document so that the shared pipeline can find the original editor. */
    fun configureDocument(document: Document, originalEditorId: EditorId, offset: Int) {
      document.putUserData(REMOTE_ORIGINAL_EDITOR, originalEditorId to offset)
    }

    /**
     * `true` when [editor] hosts the backend document created by [configureDocument];
     * checked by `CommandInsertHandler`, which runs on the backend in the remote flow.
     */
    internal fun isRemoteBackendEditor(editor: Editor): Boolean =
      editor.document.getUserData(REMOTE_ORIGINAL_EDITOR) != null
  }

  /**
   * Called on the JetBrains Client (FRONTEND) only — from the typed handler, the completion service
   * and the `VirtualFileCustomDataConsumer` in `intellij.platform.completion.frontend.split`.
   */
  object Frontend {
    private val SUFFIX = Key.create<Char>("completion.command.non.writable.remote.frontend.suffix")
    private val REMOTE_FRONTEND_EDITOR = Key.create<Boolean>("completion.command.non.writable.remote.frontend.editor")

    /**
     * Remembers the command completion trigger suffix for a file on the frontend.
     * Synchronized from the backend on file load, so that [suffix] is a cheap
     * pre-filter available right in `NonWriteAccessTypedHandler.isApplicable` on EDT.
     */
    fun setSuffix(virtualFile: VirtualFile, suffix: Char?) {
      virtualFile.putUserData(SUFFIX, suffix)
    }

    fun suffix(virtualFile: VirtualFile): Char? =
      virtualFile.getUserData(SUFFIX)

    /** Marks the command inlay editor, see [isRemoteFrontendEditor]. */
    fun configureEditor(editor: Editor) {
      editor.putUserData(REMOTE_FRONTEND_EDITOR, true)
    }

    /**
     * `true` for the frontend command inlay editor marked by [configureEditor];
     * checked by `CommandCompletionListener`, which runs where the lookup UI lives — on the client.
     */
    internal fun isRemoteFrontendEditor(editor: Editor): Boolean =
      editor.getUserData(REMOTE_FRONTEND_EDITOR) == true
  }

  private fun findApplicableFactory(editor: Editor, offset: Int, charTyped: Char): CommandCompletionFactory? {
    if (!isEnabled()) return null
    val project = editor.project ?: return null
    val document = editor.document
    if (offset !in 0..document.textLength) return null
    if (offset < document.textLength && StringUtil.isJavaIdentifierPart(document.immutableCharSequence[offset])) return null

    val targetFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
    if (InjectedLanguageManager.getInstance(project).findInjectedElementAt(targetFile, offset) != null) return null

    val factory = project.service<CommandCompletionService>().getFactory(targetFile.language) ?: return null
    if (!DumbService.getInstance(project).isUsableInCurrentContext(factory)) return null
    return factory.takeIf { it.suffix() == charTyped && it.isApplicable(targetFile, offset) }
  }
}
