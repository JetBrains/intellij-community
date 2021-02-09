// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.util.indexing.impl.storage.FileBasedIndexLayoutSettings
import java.nio.file.Files

internal object CorruptionMarker {
  private const val CORRUPTION_MARKER_NAME = "corruption.marker"

  private val corruptionMarker
    get() = PathManager.getIndexRoot().resolve(CORRUPTION_MARKER_NAME)

  @JvmStatic
  fun requestInvalidation() {
    FileBasedIndexImpl.LOG.info("Requesting explicit indices invalidation")
    try {
      Files.newOutputStream(corruptionMarker).close()
    }
    catch (ignore: Throwable) {
    }
  }

  @JvmStatic
  fun requireInvalidation(): Boolean {
    return IndexInfrastructure.hasIndices() && Files.exists(corruptionMarker)
  }

  @JvmStatic
  fun dropIndexes() {
    val indexRoot = PathManager.getIndexRoot().toFile()
    FileUtil.deleteWithRenaming(indexRoot)
    indexRoot.mkdirs()
    // serialization manager is initialized before and use removed index root so we need to reinitialize it
    SerializationManagerEx.getInstanceEx().reinitializeNameStorage()
    ID.reinitializeDiskStorage()
    PersistentIndicesConfiguration.saveConfiguration()
    FileUtil.delete(corruptionMarker)
    FileBasedIndexInfrastructureExtension.EP_NAME.extensions.forEach { it.resetPersistentState() }
    FileBasedIndexLayoutSettings.saveCurrentLayout()
  }
}