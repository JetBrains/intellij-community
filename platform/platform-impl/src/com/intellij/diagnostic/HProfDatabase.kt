/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:ApiStatus.Internal

package com.intellij.diagnostic

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.createDirectories
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Stores list of hprof files and keeps the temporary directory where they are stored clean.
 */
@ApiStatus.Internal
class HProfDatabase(tmpDirectory: Path) {

  // All hprof files should be in $tmpDirectory/hprof-temp/
  private val hprofTempDirectory: Path = tmpDirectory.resolve("hprof-temp").toAbsolutePath()

  private val LOCK = Any()

  companion object {
    private val LOG = Logger.getInstance(HProfDatabase::class.java)
  }

  fun cleanupHProfFiles(pathsToKeep: List<Path>) {
    synchronized(LOCK) {
      try {
        // Delete all files in temp directory, but ones on the list of kept files
        if (Files.isDirectory(hprofTempDirectory, LinkOption.NOFOLLOW_LINKS)) {
          val pathsToKeepSet = pathsToKeep.map(Path::toAbsolutePath).toSet()
          Files.newDirectoryStream(hprofTempDirectory).use { stream ->
            stream.forEach { path ->
              if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                  !pathsToKeepSet.contains(path.toAbsolutePath())) {
                Files.delete(path)
              }
            }
          }
        }
      }
      catch (e: IOException) {
        LOG.warn("Exception while cleaning hprof files", e)
      }
    }
  }

  fun createHprofTemporaryFilePath(): Path {
    val name = ApplicationNamesInfo.getInstance().productName.replace(' ', '-').lowercase(Locale.US)
    hprofTempDirectory.createDirectories()
    return hprofTempDirectory.resolve("heapDump-$name-${System.currentTimeMillis()}.hprof")
  }
}

val hprofDatabase: HProfDatabase = HProfDatabase(Paths.get(PathManager.getTempPath()))
