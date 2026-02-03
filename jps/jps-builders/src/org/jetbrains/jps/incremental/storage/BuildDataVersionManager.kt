// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.PersistentHashMapValueStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

@ApiStatus.Internal
interface BuildDataVersionManager {
  fun versionDiffers(): Boolean

  fun saveVersion()
}

private val VERSION = 39 +
                      (if (PersistentHashMapValueStorage.COMPRESSION_ENABLED) 1 else 0) +
                      (if (JavaBuilderUtil.isDepGraphEnabled()) 2 else 0)

internal class BuildDataVersionManagerImpl(private val versionFile: Path) : BuildDataVersionManager {
  private var versionDiffers: Boolean? = null

  override fun versionDiffers(): Boolean {
    val cached = versionDiffers
    if (cached != null) {
      return cached
    }

    try {
      val data = ByteBuffer.wrap(Files.readAllBytes(versionFile))
      val diff = data.getInt(0) != VERSION
      versionDiffers = diff
      return diff
    }
    catch (_: NoSuchFileException) {
      // treat it as a new dir
      return false
    }
    catch (e: IOException) {
      logger<BuildDataManager>().warn(e)
    }
    return true
  }

  override fun saveVersion() {
    val differs = versionDiffers
    if (differs != null && !differs) {
      return
    }

    try {
      Files.createDirectories(versionFile.parent)
      Files.write(versionFile, ByteBuffer.allocate(Integer.BYTES).putInt(VERSION).array())
    }
    catch (e: IOException) {
      logger<BuildDataManager>().warn(e)
    }
  }
}