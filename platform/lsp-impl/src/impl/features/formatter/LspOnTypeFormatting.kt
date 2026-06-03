package com.intellij.platform.lsp.impl.features.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.customization.LspOnTypeFormattingSupport
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.util.applyTextEdits
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.FormattingOptions

/**
 * Extends [TypedActionHandlerBase] to intercept keystrokes before the IDE's default brace/quote handling,
 * allowing LSP on-type formatting to process trigger characters (braces, commas, semicolons, etc.) first.
 *
 * [TypedHandlerDelegate][com.intellij.codeInsight.editorActions.TypedHandlerDelegate] cannot be used here because
 * its implementations run after brace/quote handling in [TypedHandler][com.intellij.codeInsight.editorActions.TypedHandler].
 */
internal class LspTypedActionHandler(originalHandler: TypedActionHandler?) : TypedActionHandlerBase(originalHandler) {

  @RequiresWriteLock
  override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
    val shouldPerformOnTypeFormatting = shouldPerformOnTypeFormatting(editor)
    // make sure that all handlers finished sync work before calling performOnTypeFormatting()
    myOriginalHandler?.execute(editor, charTyped, dataContext)
    if (!shouldPerformOnTypeFormatting) return

    val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
    performOnTypeFormatting(editor, virtualFile, charTyped)
  }
}

internal class LspOnTypeFormattingEnterHandler : EnterHandlerDelegate {

  @RequiresWriteLock
  override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
    val virtualFile = file.originalFile.virtualFile ?: return EnterHandlerDelegate.Result.Continue
    if (!shouldPerformOnTypeFormatting(editor)) return EnterHandlerDelegate.Result.Continue
    performOnTypeFormatting(editor, virtualFile, '\n')
    return EnterHandlerDelegate.Result.Continue
  }
}

// Skip formatting when multiple carets, selection exists, and live template or completion is active
private fun shouldPerformOnTypeFormatting(editor: Editor): Boolean {
  if (editor.caretModel.caretCount > 1) return false
  if (editor.selectionModel.hasSelection()) return false
  return isOnTypeFormattingAllowed(editor)
}

@RequiresWriteLock
private fun performOnTypeFormatting(editor: Editor, virtualFile: VirtualFile, charTyped: Char) {
  val project = editor.project ?: return
  if (!virtualFile.isInLocalFileSystem || virtualFile is VirtualFileWindow) return
  val document = FileDocumentManager.getInstance().getCachedDocument(virtualFile) ?: return
  val modificationStamp = document.modificationStamp

  LspCoroutineScopeService.getInstance(project).cs.launch {
    // Complete the LSP on-type formatting request in a read action and apply the received text edits in a write action.
    // The read action is restarted if the document changes between the read and write parts of the coroutine.
    // If the modification stamp changes, or completion/live template becomes active, formatting is skipped.
    val (lspClient, params) = readAction {
      if (document.modificationStamp != modificationStamp || !isOnTypeFormattingAllowed(editor)) return@readAction null to null
      val client = LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(virtualFile)
                     .firstOrNull { isClientApplicable(it, virtualFile, charTyped) } ?: return@readAction null to null
      val params = createParams(client, virtualFile, editor, charTyped) ?: return@readAction null to null
      return@readAction client to params
    }

    val textEdits = lspClient?.sendRequestSync { it.textDocumentService.onTypeFormatting(params) } ?: return@launch

    readAndEdtWriteAction {
      if (document.modificationStamp != modificationStamp || !isOnTypeFormattingAllowed(editor)) return@readAndEdtWriteAction value(Unit)
      return@readAndEdtWriteAction writeCommandAction(project, LspBundle.message("command.name.lsp.on.type.formatting")) {
        applyTextEdits(document, textEdits)
        Unit
      }
    }
  }
}

// check that a live template or completion is not active
private fun isOnTypeFormattingAllowed(editor: Editor): Boolean {
  val project = editor.project ?: return false
  if (project.isDefault) return false
  if (LookupManager.getActiveLookup(editor) != null) return false
  return TemplateManager.getInstance(project).getActiveTemplate(editor) == null
}

private fun isClientApplicable(client: LspClientImpl, virtualFile: VirtualFile, char: Char): Boolean {
  if (client.descriptor.lspCustomization.onTypeFormattingCustomizer !is LspOnTypeFormattingSupport) return false
  val triggerCharacters = client.getOnTypeFormattingTriggerCharacters(virtualFile) ?: return false
  return char.toString() in triggerCharacters
}

@RequiresReadLock
private fun createParams(client: LspClientImpl, virtualFile: VirtualFile, editor: Editor, char: Char): DocumentOnTypeFormattingParams? {
  val project = editor.project ?: return null
  val offset = editor.caretModel.offset

  val docPosition = client.documentMapping.getDocumentPosition(virtualFile, editor.document, offset) ?: return null

  val indentOptions = CodeStyle.getIndentOptions(project, virtualFile)
  val formattingOptions = FormattingOptions().apply {
    tabSize = indentOptions.TAB_SIZE
    isInsertSpaces = !indentOptions.USE_TAB_CHARACTER
  }

  return DocumentOnTypeFormattingParams(
    docPosition.document.id,
    formattingOptions,
    docPosition.position,
    char.toString(),
  )
}
