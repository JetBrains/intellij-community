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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.seconds

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

/** The inline editor of the last command inlay, while it is still waiting out [INLINE_EDITOR_RELEASE_DELAY]. */
private val PENDING_INLINE_EDITOR = Key.create<Disposable>("completion.command.non.writable.pending.inline.editor")

/**
 * How long the command inlay's inline editor outlives the inlay itself, so that a remote insert forwarded to the
 * backend still finds its bound document and editor.
 *
 * Only the editor is kept: the inlay's disposal already removed the text field from the editor's component hierarchy,
 * and `EditorTextField.removeNotify` keeps the editor alive once `setDisposedWith` put the field into manual mode,
 * so nothing is visible during this window.
 */
private val INLINE_EDITOR_RELEASE_DELAY = 5.seconds

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class NonWriteAccessCommandCompletionService(
  private val coroutineScope: CoroutineScope,
) {

  fun insertNewEditor(editor: Editor) {
    insertNewEditorImpl(editor, {}, null)
  }

  /**
   * Shows the command completion inlay, letting the caller set up the document of its inline text field.
   *
   * [prepareCompletion] is invoked on a background thread once the inlay is shown; completion starts only
   * if it returns `true`, otherwise the inlay is disposed.
   */
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
    // the key is set while the inlay is shown and cleared when it is disposed: in the remote flow the original
    // editor keeps the focus during the backend request, so a second keystroke can get here before the first
    // one is done, and a second inlay would be installed at the same offset
    if (editor.getUserData(INSTALLED_EDITOR) != null) return

    val textField = LanguageTextField(FileTypes.PLAIN_TEXT.language, project, "", true).apply {
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
    Disposer.register(componentInlay) {
      editor.replace(INSTALLED_EDITOR, componentInlay, null)
    }
    EditorUtil.disposeWithEditor(editor, componentInlay)
    // The inline editor must outlive the inlay: in the remote flow the command is executed by the *backend*
    // CommandInsertHandler, from a ChooseItemAction forwarded over the protocol, which lands after the frontend
    // lookup — and with it the inlay — is gone. Releasing the field here tears down its backend binding while
    // that action is still in flight, and the command is silently dropped. The backend itself gives up on the
    // insert after 2s (ACCEPT_INSERT_TIMEOUT in BackendCompletionLookupMirror), so waiting out
    // [INLINE_EDITOR_RELEASE_DELAY] covers the whole window; the host editor's lifetime is just a backstop.
    // At most one inline editor ever lingers per host editor: the [INSTALLED_EDITOR] guard above means no inlay is
    // shown right now, so anything still parked here belongs to an already disposed inlay and is released at once
    // instead of waiting out its own delay. Disposal is idempotent, so its pending release is a no-op.
    editor.getUserData(PENDING_INLINE_EDITOR)?.let { Disposer.dispose(it) }
    val textFieldDisposable = Disposer.newDisposable("command completion non-writable inline editor")
    EditorUtil.disposeWithEditor(editor, textFieldDisposable)
    editor.putUserData(PENDING_INLINE_EDITOR, textFieldDisposable)
    Disposer.register(textFieldDisposable) {
      editor.replace(PENDING_INLINE_EDITOR, textFieldDisposable, null)
    }
    textField.setDisposedWith(textFieldDisposable)
    Disposer.register(componentInlay) {
      coroutineScope.launch(Dispatchers.EDT) {
        delay(INLINE_EDITOR_RELEASE_DELAY)
        Disposer.dispose(textFieldDisposable)
      }
    }

    val inlayedEditor = textField.getEditor(true) ?: run {
      Disposer.dispose(componentInlay)
      return
    }
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

    /**
     * [offset] comes from the frontend caret over RPC, so it may be stale by a round trip
     * and is not guaranteed to be inside the backend document — it is validated, not trusted.
     */
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
    // the offset is always valid for a local caret, but it can be an out-of-date frontend caret here, see [Backend.isApplicable]
    if (offset !in 0..document.textLength) return null
    // the caret may be at the very end of the document, then there is no character to look at
    if (offset < document.textLength && StringUtil.isJavaIdentifierPart(document.immutableCharSequence[offset])) return null

    val targetFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
    if (InjectedLanguageManager.getInstance(project).findInjectedElementAt(targetFile, offset) != null) return null

    val factory = project.service<CommandCompletionService>().getFactory(targetFile.language) ?: return null
    if (!DumbService.getInstance(project).isUsableInCurrentContext(factory)) return null
    return factory.takeIf { it.suffix() == charTyped && it.isApplicable(targetFile, offset) }
  }
}
