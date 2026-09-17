// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.dnd

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathTransfer
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

/**
 * Copies a dropped file into a directory that can be in another environment.
 *
 * A drop that crosses an environment, for example a drop from a Docker container into a local
 * project, is always a copy. A move refactoring cannot cross a file system, and the two sides have
 * different file system providers, so the copy goes through the EEL API.
 *
 * @see PathFlavorProvider
 */
@ApiStatus.Internal
object DroppedFileCopy {
  private val LOG = Logger.getInstance(DroppedFileCopy::class.java)

  /**
   * Returns true when a path in [sources] is not in the same environment as [destinationDir].
   *
   * The check compares the EEL descriptor of each side. Call it on a background thread, because a
   * path can come from a custom file system provider.
   */
  @JvmStatic
  fun isAcrossEnvironments(sources: List<Path>, destinationDir: Path): Boolean {
    val destination = destinationDir.getEelDescriptor()
    return sources.any { it.getEelDescriptor() != destination }
  }

  /**
   * Copies each path in [sources] into [destinationDir] with the EEL API.
   *
   * @return the created targets, in the order of [sources]. The list is empty when the user declines the overwrite question.
   */
  suspend fun copy(project: Project, destinationDir: Path, sources: List<Path>): List<Path> {
    val items = sources.mapNotNull { source ->
      val name = source.fileName?.toString() ?: return@mapNotNull null
      Item(source, name, destinationDir.resolve(name))
    }
    if (items.isEmpty()) return emptyList()

    val existing = withContext(Dispatchers.IO) { items.filter { Files.exists(it.target) } }
    if (existing.isNotEmpty() && !confirmOverwrite(project, existing.map { it.name })) return emptyList()

    val copied = ArrayList<Path>(items.size)
    var failedItem: Item? = null
    var failure: Exception? = null
    withBackgroundProgress(project, IdeBundle.message("dnd.copy.progress.title")) {
      withContext(Dispatchers.IO) {
        reportRawProgress { reporter ->
          for (item in items) {
            reporter.text(IdeBundle.message("dnd.copy.progress.item", item.name))
            try {
              EelPathTransfer.walkingTransfer(item.source, item.target, removeSource = false, copyAttributes = true)
            }
            catch (e: Exception) {
              rethrowControlFlowException(e)
              LOG.warn("Cannot copy ${item.source} to ${item.target}", e)
              failedItem = item
              failure = e
              break
            }
            copied.add(item.target)
          }
        }
      }
    }

    val item = failedItem
    val error = failure
    if (item != null && error != null) {
      showError(project, item.name, error)
    }
    return copied
  }

  /**
   * Starts [copy] on a project scope and refreshes [destinationDir] in the VFS when the copy ends,
   * so a view of the destination shows the new files. Use it from a caller that has no coroutine
   * scope, for example from a Swing drop handler.
   */
  @JvmStatic
  fun copyInBackground(project: Project, destinationDir: Path, sources: List<Path>) {
    project.service<DroppedFileCopyScope>().scope.launch {
      try {
        copy(project, destinationDir, sources)
      }
      finally {
        refreshDestination(destinationDir)
      }
    }
  }

  private fun refreshDestination(destinationDir: Path) {
    val directory = VirtualFileManager.getInstance().findFileByNioPath(destinationDir) ?: return
    VfsUtil.markDirtyAndRefresh(true, true, true, directory)
  }

  private suspend fun confirmOverwrite(project: Project, names: List<String>): Boolean {
    val message =
      if (names.size == 1) IdeBundle.message("dnd.copy.overwrite.message.one", names.first())
      else IdeBundle.message("dnd.copy.overwrite.message.many", names.size)
    return onEdt {
      Messages.showYesNoDialog(project, message, IdeBundle.message("dnd.copy.overwrite.title"), Messages.getWarningIcon()) == Messages.YES
    }
  }

  private suspend fun showError(project: Project, name: String, error: Exception) {
    onEdt {
      Messages.showErrorDialog(
        project,
        IdeBundle.message("dnd.copy.error.message", name, error.message ?: ""),
        IdeBundle.message("dnd.copy.error.title"),
      )
    }
  }

  /**
   * Runs [action] on the EDT under [ModalityState.any].
   *
   * The drop can come from a modal dialog, for example from the file chooser. A dialog with the
   * default modality would then stay invisible until the user closes that dialog.
   */
  private suspend fun <T> onEdt(action: () -> T): T =
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) { action() }

  private data class Item(val source: Path, val name: String, val target: Path)
}

@Service(Service.Level.PROJECT)
internal class DroppedFileCopyScope(val scope: CoroutineScope)
