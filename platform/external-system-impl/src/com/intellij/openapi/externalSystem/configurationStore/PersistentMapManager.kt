/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.*
import java.io.IOException
import java.nio.file.Path

private val LOG = logger<PersistentMapManager<Any>>()

class PersistentMapManager<VALUE>(name: String, dir: Path, valueExternalizer: DataExternalizer<VALUE>, parentDisposable: Disposable, formatVersion: Int, corruptedHandler: () -> Unit) {
  private val file = dir.resolve(name)

  private val fileInitiallyExisted = file.exists()

  @Volatile
  private var storageCreated = false

  fun get(key: String) = if (fileInitiallyExisted || storageCreated) storage.get(key) else null

  fun remove(key: String) {
    put(key, null)
  }

  fun put(key: String, value: VALUE?) {
    if (value != null) {
      storage.put(key, value)
    }
    else if (fileInitiallyExisted || storageCreated) {
      storage.remove(key)
    }
  }

  val isDirty: Boolean
    get() = if (storageCreated) storage.isDirty else false

  private val storage by lazy {
    val versionFile = dir.resolve("$name.version")

    fun createMap() = PersistentHashMap(file.toFile(), EnumeratorStringDescriptor.INSTANCE, valueExternalizer)

    fun deleteFileAndWriteLatestFormatVersion() {
      dir.deleteChildrenStartingWith(file.fileName.toString())
      file.delete()
      versionFile.outputStream().use { it.write(formatVersion) }

      corruptedHandler()
    }

    val data = try {
      val fileVersion = versionFile.inputStreamIfExists()?.use { it.read() } ?: -1
      if (fileVersion != formatVersion) {
        deleteFileAndWriteLatestFormatVersion()
      }
      createMap()
    }
    catch (e: IOException) {
      LOG.info(e)
      deleteFileAndWriteLatestFormatVersion()
      createMap()
    }

    Disposer.register(parentDisposable, Disposable { data.close() })
    storageCreated = true
    data
  }

  fun forceSave() {
    if (storageCreated) {
      storage.force()
    }
  }
}