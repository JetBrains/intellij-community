// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage
import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.impl.storage.IndexLayoutPersistentSettings
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.write
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readText

@ApiStatus.Internal
object CorruptionMarker {
  private const val CORRUPTION_MARKER_NAME = "corruption.marker"
  private const val MARKED_AS_DIRTY_REASON = "Indexes marked as dirty (IDE is expected to be work)"
  private const val FORCE_REBUILD_REASON = "Indexes were forcibly marked as corrupted"
  private const val EXPLICIT_INVALIDATION_REASON = "Explicit index invalidation"

  private val corruptionMarker
    get() = PathManager.getIndexRoot().resolve(CORRUPTION_MARKER_NAME)

  @JvmStatic
  fun markIndexesAsDirty() {
    createCorruptionMarker(MARKED_AS_DIRTY_REASON)
  }

  @JvmStatic
  fun markIndexesAsClosed() {
    val corruptionMarkerExists = corruptionMarker.exists()
    if (corruptionMarkerExists) {
      try {
        if (corruptionMarker.readText() == MARKED_AS_DIRTY_REASON) {
          corruptionMarker.deleteExisting()
        }
      }
      catch (ignored: Exception) { }
    }
  }

  @JvmStatic
  fun requestInvalidation() {
    FileBasedIndexImpl.LOG.info("Explicit index invalidation has been requested")
    createCorruptionMarker(EXPLICIT_INVALIDATION_REASON)
  }

  @JvmStatic
  fun requireInvalidation(): Boolean {
    val corruptionMarkerExists = corruptionMarker.exists()
    if (corruptionMarkerExists) {
      val message = "Indexes are corrupted and will be rebuilt"
      try {
        val corruptionReason = corruptionMarker.readText()
        FileBasedIndexImpl.LOG.info("$message (reason = $corruptionReason)")
      }
      catch (_: Exception) {
        FileBasedIndexImpl.LOG.info(message)
      }
    }
    return IndexInfrastructure.hasIndices() && corruptionMarkerExists
  }

  @JvmStatic
  fun dropIndexes() {
    FileBasedIndexImpl.LOG.info("Dropping indexes...")
    val indexRoot = PathManager.getIndexRoot()

    //FIXME RC: this method of resetting the indexes works badly with mmapped storages for IndexingFlag, PersistentSubIndexerRetriever,
    //          etc -- because there is no way to 'notify' those storages about the need to re-create/reset to 0, those storages
    //          will just start failing with IOException("Parent dir[...] is not exist/was removed") -- see MMappedFileStorage...start().
    //          Really, the method works questionably even with regular storages, before mmapped -- most storages implementations
    //          are not well-suited to find all their files have just disappeared -- but regular storages have some chance to work
    //          around that sometimes, while mmapped storages follow 'if something is off => don't overthink, fail early' principle.
    //          ...Actually, I don't think it is possible to implement ReIndexing reliably without IDE restart.

    //To prove this is the main reason for apt. error in MMappedFileStorage
    //FIXME RC: drop after proved
    MMappedFileStorage.DEBUG_INDEXES_WAS_DROPPED = true

    if (Files.exists(indexRoot)) {
      val filesToBeIgnored = FileBasedIndexInfrastructureExtension.EP_NAME.extensionList.mapNotNull { it.persistentStateRoot }.toSet() +
                             ID.INDICES_ENUM_FILE +
                             IndexLayoutPersistentSettings.INDICES_LAYOUT_FILE
      indexRoot.directoryStreamIfExists { dirStream ->
        dirStream.forEach {
          if (!filesToBeIgnored.contains(it.fileName.toString())) {
            FileBasedIndexImpl.LOG.debug("\tremoving: $it")
            @Suppress("UsagesOfObsoleteApi")
            FileUtil.deleteWithRenaming(it.toFile())
          }
        }
      }
    }
    else {
      Files.createDirectories(indexRoot)
    }

    if (SystemProperties.getBooleanProperty("idea.index.clear.diagnostic.on.invalidation", true)) {
      IndexDiagnosticDumper.clearDiagnostic()
    }
    FileBasedIndexImpl.LOG.info("Indexes are dropped")

    // serialization manager is initialized before and use removed index root so we need to reinitialize it
    SerializationManagerEx.getInstanceEx().reinitializeNameStorage()
    ID.reinitializeDiskStorage()
    PersistentIndicesConfiguration.saveConfiguration()
    FileUtil.delete(corruptionMarker)
    FileBasedIndexInfrastructureExtension.EP_NAME.forEachExtensionSafe { it.resetPersistentState() }
    IndexLayoutPersistentSettings.forceSaveCurrentLayout()
  }

  private fun createCorruptionMarker(reason: String) {
    try {
      corruptionMarker.write(reason)
    }
    catch (e: Exception) {
      FileBasedIndexImpl.LOG.warn(e)
    }
  }
}
