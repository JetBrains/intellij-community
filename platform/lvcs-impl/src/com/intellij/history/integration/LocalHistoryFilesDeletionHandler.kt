// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.StoredContent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileOperationsHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.util.ThrowableConsumer
import java.io.File
import java.io.IOException
import java.util.function.Function

/**
 * Compliments (or replaces) files deletion handling in [LocalHistoryEventDispatcher].
 * Unlike handling [VFileDeleteEvent], intercepts files' deletion before they are physically removed, providing a chance to access their
 * content even if it wasn't loaded, which is needed to support binary files recovery.
 *
 * @see [StoredContent.acquireContent]
 */
internal class LocalHistoryFilesDeletionHandler(
  private val facade: LocalHistoryFacade,
  private val localHistoryGateway: IdeaGateway,
) : LocalFileOperationsHandler {
  override fun delete(file: VirtualFile): Boolean {
    if (!isEnabled()) return false

    file.putUserData(DELETION_PROCESSED, Unit)

    val entry = localHistoryGateway.doCreateEntry(file, Function(::createEntryForDeletion), null)
    if (entry != null) {
      LOG.trace { "Created entry for file deletion: ${file.path}" }
      facade.deleted(localHistoryGateway.getPathOrUrl(file), entry)
    } else {
      LOG.trace { "Can't create entry for file deletion: ${file.path}" }
    }

    return false
  }

  private fun createEntryForDeletion(file: VirtualFile): IdeaGateway.ContentWithTimestamp {
    file.putUserData(DELETION_PROCESSED, Unit)
    return localHistoryGateway.acquireContentForDeletedFile(file, {
      val maxSizeBytes = getMaxSizeBytes()
      if (file.length > maxSizeBytes) {
        LOG.warn("File ${file.path} is too big to be stored in local history")
        StoredContent.UNAVAILABLE_CONTENT
      } else {
        StoredContent.acquireContent(file.contentsToByteArray(false))
      }
    })
  }

  override fun move(file: VirtualFile, toDir: VirtualFile): Boolean = false

  override fun copy(file: VirtualFile, toDir: VirtualFile, copyName: String): File? = null

  override fun rename(file: VirtualFile, newName: String): Boolean = false

  override fun createFile(dir: VirtualFile, name: String): Boolean = false

  override fun createDirectory(dir: VirtualFile, name: String): Boolean = false

  override fun afterDone(invoker: ThrowableConsumer<in LocalFileOperationsHandler, out IOException>) {}

  companion object {
    private val LOG = logger<LocalHistoryFilesDeletionHandler>()

    /**
     * [LocalHistoryEventDispatcher] still handles deletion events.
     * To avoid double processing, files processed in [LocalHistoryFilesDeletionHandler.delete] are marked with this key.
     */
    private val DELETION_PROCESSED = Key.create<Unit>("LocalHistory.DeletionProcessed")

    fun wasProcessed(file: VirtualFile) = file.getUserData(DELETION_PROCESSED) != null

    fun isEnabled() = getMaxSizeBytes() > 0

    fun getMaxSizeBytes(): Long {
      val megabytes = Registry.intValue("lvcs.store.binary.file.content.on.deletion.mb", 0)
      return if (megabytes > 0) megabytes.toLong() * FileUtilRt.MEGABYTE else 0L
    }
  }
}
