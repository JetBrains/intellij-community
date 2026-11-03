// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.workspaceFileOperations

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.LspDynamicCapabilities
import com.intellij.platform.lsp.impl.platformListeners.MOVE_REFACTORING_ID
import com.intellij.platform.lsp.impl.platformListeners.RENAME_REFACTORING_ID
import com.intellij.platform.lsp.impl.util.LspWorkspaceEditApplier
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.WorkspaceEdit
import kotlin.time.Duration.Companion.milliseconds

private val LOG = fileLogger()

// The spec allows a client to drop a response that takes too long.
private val WILL_RENAME_FILES_TIMEOUT = 1000.milliseconds

internal object LspWillRenameFilesHandler {
  /**
   * Sends the
   * [workspace/willRenameFiles](https://microsoft.github.io/language-server-protocol/specification/#workspace_willRenameFiles)
   * request before a Rename or Move refactoring changes the files, and applies the edits from the servers in the command of the refactoring.
   */
  internal fun renameOrMoveRefactoringStarted(project: Project, refactoringId: String, beforeData: RefactoringEventData) {
    val lspClients = LspClientManagerImpl.getInstanceImpl(project).getRunningClients()
      .filter { it.clientCapabilities?.workspace?.fileOperations?.willRename == true }
    if (lspClients.isEmpty()) return

    val application = ApplicationManager.getApplication()
    if (!application.isDispatchThread || application.isWriteAccessAllowed) {
      LOG.warn("Skipping workspace/willRenameFiles for '$refactoringId': the event came " +
               "${if (application.isDispatchThread) "on the EDT" else "off the EDT"}, " +
               "${if (application.isWriteAccessAllowed) "inside" else "outside"} a write action. " +
               "Expected: EDT outside a write action.")
      return
    }

    val plannedRenames = when (refactoringId) {
      RENAME_REFACTORING_ID -> getPlannedRenamesFromRenameRefactoring(beforeData)
      MOVE_REFACTORING_ID -> getPlannedRenamesFromMoveRefactoring(beforeData)
      else -> emptyList()
    }
    if (plannedRenames.isEmpty()) return

    try {
      sendWillRenameFilesRequestsAndApplyEdits(project, lspClients, plannedRenames)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.error(e)
    }
  }

  private fun getPlannedRenamesFromRenameRefactoring(beforeData: RefactoringEventData): List<LspPlannedRename> {
    val renames = beforeData.getUserData(RefactoringEventData.RENAMES_KEY) ?: return emptyList()
    return renames.mapNotNull { [element, newName] ->
      if (element !is PsiFileSystemItem || !element.isValid || element.name == newName) return@mapNotNull null
      val parentDir = element.parent as? PsiDirectory ?: return@mapNotNull null
      createPlannedRename(element, parentDir, newName)
    }
  }

  private fun getPlannedRenamesFromMoveRefactoring(beforeData: RefactoringEventData): List<LspPlannedRename> {
    val targetDir = beforeData.getUserData(RefactoringEventData.TARGET_ELEMENT_KEY) as? PsiDirectory ?: return emptyList()
    val elements = beforeData.getUserData(RefactoringEventData.PSI_ELEMENT_ARRAY_KEY) ?: return emptyList()
    return elements.mapNotNull { element ->
      // the move skips an item that is already in the target, and a file whose name already exists there
      if (element !is PsiFileSystemItem || !element.isValid || element.parent == targetDir) return@mapNotNull null
      if (element is PsiFile && targetDir.findFile(element.name) != null) return@mapNotNull null
      createPlannedRename(element, targetDir, element.name)
    }
  }

  private fun createPlannedRename(fileSystemItem: PsiFileSystemItem, newParentDir: PsiDirectory, newName: String): LspPlannedRename? {
    val fileOrDir = fileSystemItem.virtualFile
    if (fileOrDir == null || !fileOrDir.isValid) return null
    return LspPlannedRename(fileOrDir, newParentDir.virtualFile, newName)
  }

  private class LspPlannedRename(val fileOrDir: VirtualFile, val newParentDir: VirtualFile, val newName: String)

  @RequiresEdt
  private fun sendWillRenameFilesRequestsAndApplyEdits(
    project: Project,
    lspClients: List<LspClientImpl>,
    plannedRenames: List<LspPlannedRename>,
  ) {
    val lspClientToFilters = lspClients
      .mapNotNull { lspClient -> getWillRenameFilters(lspClient).takeIf { it.isNotEmpty() }?.let { lspClient to it } }
    if (lspClientToFilters.isEmpty()) return

    var workspaceEditAppliers = emptyList<LspWorkspaceEditApplier>()
    val completed = ApplicationManagerEx.getApplicationEx().runProcessWithProgressSynchronously(
      { workspaceEditAppliers = sendWillRenameFilesRequests(lspClientToFilters, plannedRenames) },
      LspBundle.message("lsp.will.rename.files.progress.title"),
      true,
      true,
      project,
      null,
      LspBundle.message("lsp.will.rename.files.skip"),
    )

    if (completed) {
      workspaceEditAppliers.forEach { it.applyWorkspaceEdit() }
    }
  }

  private fun getWillRenameFilters(lspClient: LspClientImpl): List<FileOperationFilter> {
    val staticFilters = lspClient.serverCapabilities?.workspace?.fileOperations?.willRename?.filters.orEmpty()
    val dynamicFilters = lspClient.dynamicCapabilities.getCapabilityRegistrationOptions(LspDynamicCapabilities.willRenameFiles)
      .flatMap { it.filters.orEmpty() }
    return staticFilters + dynamicFilters
  }

  @RequiresBackgroundThread
  private fun sendWillRenameFilesRequests(
    lspClientToFilters: List<Pair<LspClientImpl, List<FileOperationFilter>>>,
    plannedRenames: List<LspPlannedRename>,
  ): List<LspWorkspaceEditApplier> = runBlockingCancellable {
    lspClientToFilters.map { [lspClient, filters] ->
      async {
        val clientPlannedRenames = filterPlannedRenames(lspClient, filters, plannedRenames)
        if (clientPlannedRenames.isEmpty()) return@async null
        val edit = sendWillRenameFilesRequest(lspClient, clientPlannedRenames) ?: return@async null
        readAction { LspWorkspaceEditApplier.create(lspClient, edit) }
      }
    }.awaitAll().filterNotNull()
  }

  @RequiresBackgroundThread
  private fun filterPlannedRenames(
    lspClient: LspClientImpl,
    filters: List<FileOperationFilter>,
    plannedRenames: List<LspPlannedRename>,
  ): List<LspPlannedRename> {
    return plannedRenames.filter { plannedRename ->
      val filePath = lspClient.descriptor.getFilePath(plannedRename.fileOrDir)
      val uriScheme = plannedRename.fileOrDir.fileSystem.protocol
      val isDirectory = plannedRename.fileOrDir.isDirectory

      lspClient.descriptor.roots.any { root -> VfsUtilCore.isAncestor(root, plannedRename.fileOrDir, false) } &&
      filters.any { lspClient.globMatcher.pathMatchesFileOperationFilter(filePath, uriScheme, isDirectory, it) }
    }
  }

  private suspend fun sendWillRenameFilesRequest(lspClient: LspClientImpl, plannedRenames: List<LspPlannedRename>): WorkspaceEdit? {
    val fileRenames = plannedRenames.map {
      val oldUri = lspClient.descriptor.getFileUri(it.fileOrDir)
      val newUri = lspClient.descriptor.getFileUri(LspRenameTargetFile(it.newParentDir, it.newName, it.fileOrDir.isDirectory))
      FileRename(oldUri, newUri)
    }
    val params = RenameFilesParams(fileRenames)

    return try {
      withTimeoutOrNull(WILL_RENAME_FILES_TIMEOUT) {
        lspClient.sendRequest { it.workspaceService.willRenameFiles(params) }
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      // a failed server must not cancel the requests to other servers
      LOG.warn("${lspClient.descriptor}: workspace/willRenameFiles request failed", e)
      null
    }
  }
}

/**
 * The file or directory at the new location of a planned rename. It does not exist yet.
 * It has the file system and the path of the real file after the rename,
 * so [com.intellij.platform.lsp.api.LspClientDescriptor.getFileUri] and its overrides give the same URI for both.
 */
private class LspRenameTargetFile(
  newParentDir: VirtualFile,
  newName: String,
  private val isDirectory: Boolean,
) : FakeVirtualFile(newParentDir, newName) {

  override fun getUrl(): String = fileSystem.protocol + URLUtil.SCHEME_SEPARATOR + path
  override fun isDirectory(): Boolean = isDirectory
  override fun isValid(): Boolean = true
}
