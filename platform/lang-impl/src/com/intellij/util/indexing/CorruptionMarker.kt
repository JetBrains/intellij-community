// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.impl.storage.FileBasedIndexLayoutSettings
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.exists
import com.intellij.util.io.readText
import com.intellij.util.io.write
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import kotlin.io.path.deleteExisting

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
      catch (e: Exception) {
        FileBasedIndexImpl.LOG.info(message)
      }
    }
    return IndexInfrastructure.hasIndices() && corruptionMarkerExists
  }

  @JvmStatic
  fun dropIndexes() {
    FileBasedIndexImpl.LOG.info("Indexes are dropped")
    val indexRoot = PathManager.getIndexRoot()

    if (Files.exists(indexRoot)) {
      val filesToBeIgnored = FileBasedIndexInfrastructureExtension.EP_NAME.extensions.mapNotNull { it.persistentStateRoot }.toSet()
      indexRoot.directoryStreamIfExists { dirStream ->
        dirStream.forEach {
          if (!filesToBeIgnored.contains(it.fileName.toString())) {
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

    // serialization manager is initialized before and use removed index root so we need to reinitialize it
    SerializationManagerEx.getInstanceEx().reinitializeNameStorage()
    ID.reinitializeDiskStorage()
    PersistentIndicesConfiguration.saveConfiguration()
    FileUtil.delete(corruptionMarker)
    FileBasedIndexInfrastructureExtension.EP_NAME.extensions.forEach { it.resetPersistentState() }
    FileBasedIndexLayoutSettings.saveCurrentLayout()
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