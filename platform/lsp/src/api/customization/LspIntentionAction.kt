// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.util.applyTextEdits
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CreateFile
import org.eclipse.lsp4j.DeleteFile
import org.eclipse.lsp4j.RenameFile
import org.eclipse.lsp4j.WorkspaceEdit

/**
 * [IntentionAction] that knows how to apply [CodeAction] received from the LSP server.
 *
 * #### Implementation note
 * IntelliJ Platform API assumes that the quick fixes for diagnostics are created right at the moment of the code highlighting.
 * For performance reasons, special quick fix stubs are created at that
 * moment. Those stubs do not contain [CodeAction] objects. As soon as the IntelliJ Platform calls [IntentionAction.getText] or
 * [IntentionAction.isAvailable] on those stubs, the stubs ask the LSP server to calculate real [CodeAction] objects and, once received,
 * create [LspIntentionAction] objects. After that, those quick fix stubs delegate [getText], [isAvailable], and [invoke] calls to these
 * [LspIntentionAction] objects.
 *
 * [LspIntentionAction] objects are used not only for quick fixes, but also for other kinds of [CodeActions][CodeAction].
 * In IntelliJ Platform terms, other kinds of `CodeActions` are handled as
 * [Intention actions](https://www.jetbrains.com/help/idea/intention-actions.html).
 */
open class LspIntentionAction(protected val lspClient: LspClient, private val initialCodeAction: CodeAction) : IntentionAction {
  @Deprecated("Use the LspClient constructor")
  @Suppress("DEPRECATION")
  constructor(lspServer: LspServer, initialCodeAction: CodeAction) : this(lspServer as LspClient, initialCodeAction)

  @Deprecated("Use lspClient", ReplaceWith("lspClient"))
  @Suppress("DEPRECATION")
  protected val lspServer: LspServer
    get() = lspClient as LspServer

  // 1. If the `initialCodeAction` contains the `edit` property, it means that it is already resolved.
  // 2. If the server doesn't support code actions resolve, it also means that the `initialCodeAction` is already resolved.
  private var resolvedCodeAction: CodeAction? =
    if (initialCodeAction.edit != null ||
        lspClient.initializeResult?.capabilities?.codeActionProvider?.right?.resolveProvider != true) {
      initialCodeAction
    }
    else null

  private val codeAction: CodeAction
    get() = resolvedCodeAction ?: initialCodeAction

  private var uriToDocumentMapInitialized: Boolean = false
  private var uriToDocumentMap: Map<String, Document>? = null

  // `getFamilyName()` is not delegated to this class. The wrapper class returns an empty string. This function is never called.
  final override fun getFamilyName(): String = ""

  // `startInWriteAction()` is not delegated to this class. The wrapper class returns `false`. This function is never called.
  final override fun startInWriteAction(): Boolean = false

  override fun getText(): String = codeAction.title

  override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean = isAvailable()

  /**
   * The side effect of this function is that it resolves the [initialCodeAction] if needed,
   * and also fills the auxiliary [uriToDocumentMap] that is used later in the [invoke] function.
   */
  @RequiresReadLock
  fun isAvailable(): Boolean = isAvailable { true }

  @RequiresReadLock
  fun isAvailable(codeActionValidator: (CodeAction) -> Boolean): Boolean {
    codeAction.disabled?.let { return false }

    resolveCodeAction()

    val workspaceEdit = codeAction.edit
    if (!uriToDocumentMapInitialized) {
      uriToDocumentMap = if (workspaceEdit != null) getUriToDocumentMap(workspaceEdit) else emptyMap()
      uriToDocumentMapInitialized = true
    }

    return uriToDocumentMap != null &&
           (workspaceEdit != null || codeAction.command != null) &&
           codeActionValidator(codeAction)
  }

  @RequiresBackgroundThread
  private fun resolveCodeAction() {
    if (resolvedCodeAction != null) return

    val resolved = lspClient.sendRequestSync { it.textDocumentService.resolveCodeAction(codeAction) }
    resolvedCodeAction = resolved ?: initialCodeAction
  }

  override fun invoke(project: Project, editor: Editor, psiFile: PsiFile): Unit = invoke(psiFile.virtualFile)

  /**
   * Make sure to call [isAvailable] before calling [invoke],
   * otherwise the auxiliary [uriToDocumentMap] will be not ready and the `CodeAction` will not be applied.
   */
  @RequiresEdt
  fun invoke(contextFile: VirtualFile?) {
    val uriToDocumentMap = this.uriToDocumentMap ?: return
    val files = uriToDocumentMap.map { FileDocumentManager.getInstance().getFile(it.value) }
    if (!FileModificationService.getInstance().prepareVirtualFilesForWrite(lspClient.project, files)) return

    val workspaceEdit = codeAction.edit
    if (workspaceEdit?.changes?.isNotEmpty() == true || workspaceEdit?.documentChanges?.isNotEmpty() == true) {
      WriteAction.run<Throwable> { applyWorkspaceEdit(workspaceEdit, uriToDocumentMap) }
    }

    applyCommand(lspClient.descriptor.lspCustomization.commandsCustomizer, codeAction.command, contextFile)
  }

  private fun applyCommand(commandsSupport: LspCommandsCustomizer, command: Command?, contextFile: VirtualFile?) {
    if (commandsSupport is LspCommandsSupport && command != null && contextFile != null) {
      commandsSupport.executeCommand(lspClient, contextFile, command)
    }
  }

  /**
   * @param uriToDocumentMap it's guaranteed that it contains `Document` objects for URIs from this [workspaceEdit]
   */
  @RequiresWriteLock
  protected open fun applyWorkspaceEdit(workspaceEdit: WorkspaceEdit, uriToDocumentMap: Map<String, Document>) {
    workspaceEdit.changes?.let { changes ->
      changes.entries.forEach { entry ->
        val document = uriToDocumentMap[entry.key]!!
        if (!applyTextEdits(document, entry.value)) return@applyWorkspaceEdit
      }
    }

    val uriToCreatedDocumentMap = mutableMapOf<String, Document>()

    workspaceEdit.documentChanges?.let { documentChanges ->
      if (documentChanges.any { it.isRight && it.right !is CreateFile }) {
        // TODO support DeleteFile and RenameFile as well
        return@applyWorkspaceEdit
      }

      documentChanges.forEach { editOrResourceOperation ->
        editOrResourceOperation.left?.let { textDocumentEdit ->
          val document = uriToDocumentMap[textDocumentEdit.textDocument.uri] ?: uriToCreatedDocumentMap[textDocumentEdit.textDocument.uri]
          if (document == null) {
            thisLogger().error("No Document for ${textDocumentEdit.textDocument.uri}")
            return@applyWorkspaceEdit
          }
          if (!applyTextEdits(document, textDocumentEdit.edits)) return@applyWorkspaceEdit
        }

        editOrResourceOperation.right?.let { resourceOperation ->
          when (resourceOperation) {
            is CreateFile -> createFile(resourceOperation)?.let { uriToCreatedDocumentMap[resourceOperation.uri] = it }
            is DeleteFile -> {} // TODO implement
            is RenameFile -> {} // TODO implement
          }
        }
      }
    }
  }

  private fun createFile(createFile: CreateFile): Document? {
    val fileUri = createFile.uri
    var existingDirUri = PathUtil.getParentPath(fileUri)
    var existingDir = lspClient.descriptor.findFileByUri(existingDirUri)
    while (existingDir == null && !existingDirUri.isEmpty()) {
      existingDirUri = PathUtil.getParentPath(existingDirUri)
      existingDir = lspClient.descriptor.findFileByUri(existingDirUri)
    }

    if (existingDir == null) {
      thisLogger().warn("Ignoring CreateFile(${fileUri}): base directory not found")
      return null
    }

    val relativePath = fileUri.substring(existingDirUri.length + 1)
    val fileName = PathUtil.getFileName(relativePath)
    val relativeParentPath = PathUtil.getParentPath(relativePath)

    val parentDir = when {
      relativeParentPath.isEmpty() -> existingDir
      else -> VfsUtil.createDirectoryIfMissing(existingDir.path + "/" + relativeParentPath)
    }

    if (parentDir == null) {
      thisLogger().warn("Failed to create parent directory for CreateFile(${fileUri})")
      return null
    }

    val createdFile = parentDir.createChildData(this, fileName)
    return FileDocumentManager.getInstance().getDocument(createdFile)
      .also { if (it == null) thisLogger().warn("No Document for created file ${createdFile.path}") }
  }

  private fun getUriToDocumentMap(edit: WorkspaceEdit): Map<String, Document>? {
    val result = mutableMapOf<String, Document>()

    edit.changes?.let { changes ->
      changes.keys.forEach { documentUri ->
        val document = getDocument(documentUri) ?: return null
        result[documentUri] = document
      }
    }

    val urisToCreate = mutableSetOf<String>()

    edit.documentChanges?.let { documentChanges ->
      documentChanges.forEach { editOrResourceOperation ->
        editOrResourceOperation.left?.let { textDocumentEdit ->
          val documentUri = textDocumentEdit.textDocument.uri
          if (!urisToCreate.contains(documentUri)) {
            val version: Int? = textDocumentEdit.textDocument.version
            val document = getDocument(documentUri, version ?: -1) ?: return null
            result[documentUri] = document
          }
        }

        (editOrResourceOperation.right as? CreateFile)?.uri?.let { urisToCreate.add(it) }
      }
    }

    return result
  }

  private fun getDocument(documentUri: String, version: Int = -1): Document? {
    val file = lspClient.descriptor.findFileByUri(documentUri)
    if (file == null) {
      thisLogger().warn("File not found: $documentUri")
      return null
    }

    if (!ProjectFileIndex.getInstance(lspClient.project).isInContent(file)) {
      thisLogger().warn("File is not within the project content: $documentUri")
      return null
    }

    val document = FileDocumentManager.getInstance().getDocument(file)
    if (document == null) {
      thisLogger().warn("Document not found for file: $file")
      return null
    }

    val documentVersion = lspClient.getDocumentVersion(document)
    if (version != -1 && documentVersion != version) {
      thisLogger().info("Ignoring CodeAction for document version $version (${file.name}); " +
                        "current document version: $documentVersion")
      return null
    }

    return document
  }

  override fun generatePreview(project: Project, editor: Editor, nonPhysicalPsiFile: PsiFile): IntentionPreviewInfo {
    val uriToDocumentMap = this.uriToDocumentMap ?: return IntentionPreviewInfo.EMPTY
    val physicalPsiFile = nonPhysicalPsiFile.getOriginalFile()
    val physicalDocument = PsiDocumentManager.getInstance(project).getDocument(physicalPsiFile) ?: return IntentionPreviewInfo.EMPTY
    if (!uriToDocumentMap.containsValue(physicalDocument)) return IntentionPreviewInfo.EMPTY

    invokeForPreview(nonPhysicalPsiFile.getViewProvider().getDocument(), physicalDocument)
    return IntentionPreviewInfo.DIFF
  }

  private fun invokeForPreview(nonPhysicalDocument: Document, physicalDocument: Document) {
    val uriToDocumentMap = this.uriToDocumentMap ?: return
    val workspaceEdit = codeAction.edit ?: return

    workspaceEdit.changes?.let { changes ->
      changes.entries.forEach { entry ->
        if (physicalDocument != uriToDocumentMap[entry.key]) return@forEach
        if (!applyTextEdits(nonPhysicalDocument, entry.value)) return@invokeForPreview
      }
    }

    workspaceEdit.documentChanges?.let { documentChanges ->
      documentChanges.forEach { editOrResourceOperation ->
        editOrResourceOperation.left?.let { textDocumentEdit ->
          if (physicalDocument != uriToDocumentMap[textDocumentEdit.textDocument.uri]) return@forEach
          if (!applyTextEdits(nonPhysicalDocument, textDocumentEdit.edits)) return@invokeForPreview
        }
      }
    }
  }
}
