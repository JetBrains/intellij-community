package com.intellij.platform.lsp.impl.features.rename

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.TemplateResultListener
import com.intellij.codeInsight.template.TemplateResultListener.TemplateResult
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.Variable
import com.intellij.ide.TitledHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.customization.LspRenameSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.util.LspWorkspaceEditApplier
import com.intellij.platform.lsp.util.getRangeInDocument
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.util.DocumentUtil
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException

private const val PRIMARY_VARIABLE_NAME = "LSP_RENAME_PRIMARY"

internal class LspRenameHandler : RenameHandler, TitledHandler, DumbAware {

  override fun getActionTitle(): String {
    return LspBundle.message("lsp.rename.action.text")
  }

  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
    val project = editor.project ?: return false
    val file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return false

    return findLspClientForRename(project, file) != null
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
    if (editor == null || file == null || dataContext == null) return

    val virtualFile = file.virtualFile ?: return
    val lspClient = findLspClientForRename(project, virtualFile) ?: return

    val docPosition = lspClient.documentMapping.getDocumentPosition(virtualFile, editor.document, editor.caretModel.offset) ?: return
    val textDocumentIdentifier = docPosition.document.id
    val position = docPosition.position
    val params = PrepareRenameParams(textDocumentIdentifier, position)

    val prepareRenameResult = prepareRename(lspClient, editor, params, virtualFile)

    if (prepareRenameResult == null) return

    val (range, placeholder) = prepareRenameResult

    val initialName = placeholder ?: editor.document.getText(range)

    startInlineRename(project, editor, range, initialName) { newName ->
      val params = RenameParams(textDocumentIdentifier, position, newName)
      performRename(editor, lspClient, params)
    }
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
    // Not used for LSP-based rename
  }

  private fun findLspClientForRename(project: Project, file: VirtualFile): LspClientImpl? {
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null

    return LspClientManagerImpl.getInstanceImpl(project)
      .getClientsWithThisFileOpen(file)
      .find {
        val customizer = it.descriptor.lspCustomization.renameCustomizer
        customizer is LspRenameSupport && it.supportsRename(file) && customizer.shouldRunRename(psiFile)
      }
  }

  private fun prepareRename(
    lspClient: LspClientImpl,
    editor: Editor,
    params: PrepareRenameParams,
    virtualFile: VirtualFile,
  ): Pair<TextRange, String?>? {
    val renameCustomizer = lspClient.descriptor.lspCustomization.renameCustomizer as LspRenameSupport
    val document = editor.document
    val offset = editor.caretModel.offset

    // If server doesn't support prepareRename, fall back to word-at-range
    if (!lspClient.supportsPrepareRename(virtualFile)) {
      val wordRange = renameCustomizer.getRenameableRangeAtOffset(document, offset)
      return wordRange?.let { it to null }
    }

    val result = try {
      runWithModalProgressBlocking(
        lspClient.project,
        LspBundle.message("lsp.rename.prepare.progress.title")
      ) {
        lspClient.sendRequest { it.textDocumentService.prepareRename(params) }
      }
    }
    catch (e: ResponseErrorException) {
      e.responseError?.message?.let { showErrorHint(editor, it) }
      null
    }

    return result?.map(
      { range ->
        getRangeInDocument(document, range)?.let { it to null }
      },
      { prepareRenameResult ->
        getRangeInDocument(document, prepareRenameResult.range)?.let { it to prepareRenameResult.placeholder }
      },
      {
        val wordRange = renameCustomizer.getRenameableRangeAtOffset(document, offset)
        wordRange?.let { it to null }
      }
    )
  }

  private fun performRename(editor: Editor, lspClient: LspClientImpl, params: RenameParams) {
    try {
      runWithModalProgressBlocking(lspClient.project, LspBundle.message("lsp.rename.progress.title")) {
        val workspaceEdit = lspClient.sendRequest { it.textDocumentService.rename(params) } ?: return@runWithModalProgressBlocking

        readAndEdtWriteAction {
          val applier = LspWorkspaceEditApplier.create(lspClient, workspaceEdit) ?: return@readAndEdtWriteAction value(Unit)
          writeAction { applier.applyWorkspaceEdit() }
        }
      }
    }
    catch (e: ResponseErrorException) {
      e.responseError?.message?.let { showErrorHint(editor, it) }
    }
  }

  private fun showErrorHint(editor: Editor, errorMessage: @NlsSafe String) =
    HintManager.getInstance().showErrorHint(
      editor, errorMessage,
      editor.caretModel.offset, editor.caretModel.offset,
      HintManager.ABOVE,
      HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.UPDATE_BY_SCROLLING,
      3000
    )
}

private fun startInlineRename(
  project: Project,
  editor: Editor,
  textRange: TextRange,
  initialName: String,
  onRename: (String) -> Unit,
) {
  val document = editor.document
  val commandName = RefactoringBundle.message("renaming.command.name", initialName)

  // Start a mark action to group all rename operations into a single undo unit
  // StartMarkAction.start must be called inside a command
  var startMarkAction: StartMarkAction? = null
  var alreadyStarted = false
  CommandProcessor.getInstance().executeCommand(project, {
    try {
      startMarkAction = StartMarkAction.start(editor, project, commandName)
    }
    catch (_: StartMarkAction.AlreadyStartedException) {
      alreadyStarted = true
    }
  }, commandName, null)
  if (alreadyStarted || startMarkAction == null) return

  val finishMarkAction: () -> Unit = {
    FinishMarkAction.finish(project, editor, startMarkAction)
  }

  val template = TemplateManager.getInstance(project).createTemplate("", "")

  // text before renaming range + variable + text after renaming range
  val documentText = document.text
  template.addTextSegment(documentText.substring(0, textRange.startOffset))
  template.addVariableSegment(PRIMARY_VARIABLE_NAME)
  template.addTextSegment(documentText.substring(textRange.endOffset))

  val expression = ConstantNode(initialName)
  template.addVariable(Variable(PRIMARY_VARIABLE_NAME, expression, expression, true, false))

  WriteCommandAction.writeCommandAction(project).withName(commandName).run<Throwable> {
    // Configure template for inline editing
    template.setInline(true)
    template.setToIndent(false)
    template.isToShortenLongNames = false
    template.isToReformat = false

    editor.caretModel.moveToOffset(0)

    // Delete the text range content (template will insert its content)
    DocumentUtil.executeInBulk(document) {
      document.deleteString(textRange.startOffset, textRange.endOffset)
    }

    fun restoreDocument() {
      WriteAction.run<Throwable> {
        document.replaceString(0, document.textLength,
                               documentText.substring(0, textRange.startOffset) +
                               initialName +
                               documentText.substring(textRange.endOffset))
      }
    }

    val templateState = TemplateManager.getInstance(project).runTemplate(editor, template)

    templateState.addTemplateStateListener(TemplateResultListener { result ->
      try {
        when (result) {
          // undo -> cancelled
          TemplateResult.Canceled -> Unit
          // esc -> brokenOff
          TemplateResult.BrokenOff -> {
            restoreDocument()
          }
          TemplateResult.Finished -> {
            val newName = templateState.getVariableValue(PRIMARY_VARIABLE_NAME)?.text
            restoreDocument()
            if (!newName.isNullOrBlank() && newName != initialName) {
              onRename(newName)
            }
          }
        }
      }
      finally {
        // Changes the default command name to ensure the undo action displays proper command name instead of "Go to Next Code Template Tab"
        if (CommandProcessor.getInstance().currentCommandName != null) {
          CommandProcessor.getInstance().setCurrentCommandName(commandName)
        }
        finishMarkAction()
      }
    })
  }
}
