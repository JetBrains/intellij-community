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

import com.intellij.configurationStore.deserializeElementFromBinary
import com.intellij.configurationStore.serializeElementToBinary
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.ByteSequence
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.*
import com.intellij.util.loadElement
import com.intellij.util.write
import org.jdom.Element
import java.io.ByteArrayInputStream
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

internal interface ExternalSystemStorage {
  val isDirty: Boolean

  fun remove(name: String)

  fun read(name: String): Element?

  fun write(name: String, element: Element)

  fun forceSave()

  fun rename(oldName: String, newName: String)
}

private fun nameToFilename(name: String) = "${FileUtil.sanitizeFileName(name, false)}.xml"

internal class FileSystemExternalSystemStorage(project: Project) : ExternalSystemStorage {
  override val isDirty = false

  private val dir = ExternalProjectsDataStorage.getProjectConfigurationDir(project).resolve("modules")

  private var hasSomeData: Boolean

  init {
    val fileAttributes = dir.basicAttributesIfExists()
    if (fileAttributes == null) {
      hasSomeData = false
    }
    else if (fileAttributes.isRegularFile) {
      // old binary format
      dir.parent.deleteChildrenStartingWith(dir.fileName.toString())
      hasSomeData = false
    }
    else {
      LOG.assertTrue(fileAttributes.isDirectory)
      hasSomeData = true
    }
  }

  private fun nameToPath(name: String) = dir.resolve(nameToFilename(name))

  override fun forceSave() {
  }

  override fun remove(name: String) {
    if (!hasSomeData) {
      return
    }

    nameToPath(name).delete()
  }

  override fun read(name: String): Element? {
    if (!hasSomeData) {
      return null
    }

    return nameToPath(name).inputStreamIfExists()?.use {
      loadElement(it)
    }
  }

  override fun write(name: String, element: Element) {
    hasSomeData = true
    element.write(nameToPath(name))
  }

  override fun rename(oldName: String, newName: String) {
    if (!hasSomeData) {
      return
    }

    val oldFile = nameToPath(oldName)
    if (oldFile.exists()) {
      oldFile.move(nameToPath(newName))
    }
  }
}

// not used for now, https://upsource.jetbrains.com/IDEA/review/IDEA-CR-20673, later PersistentHashMap will be not used.
@Suppress("unused")
internal class BinaryExternalSystemStorage(project: Project) : ExternalSystemStorage {
  override fun forceSave() {
    moduleStorage.forceSave()
  }

  override val isDirty: Boolean
    get() = moduleStorage.isDirty

  @Suppress("INTERFACE_STATIC_METHOD_CALL_FROM_JAVA6_TARGET")
  val moduleStorage = PersistentMapManager("modules", ExternalProjectsDataStorage.getProjectConfigurationDir(project), ByteSequenceDataExternalizer.INSTANCE, project, 0) {
    StartupManager.getInstance(project).runWhenProjectIsInitialized {
      val externalProjectManager = ExternalProjectsManager.getInstance(project)
      externalProjectManager.runWhenInitialized {
        externalProjectManager.externalProjectsWatcher.markDirtyAllExternalProjects()
      }
    }
  }

  override fun remove(name: String) {
    moduleStorage.remove(name)
  }

  override fun read(name: String): Element? {
    val data = moduleStorage.get(name) ?: return null
    return ByteArrayInputStream(data.bytes, data.offset, data.length).use { deserializeElementFromBinary(it) }

  }

  override fun write(name: String, element: Element) {
    val byteOut = BufferExposingByteArrayOutputStream()
    serializeElementToBinary(element, byteOut)
    moduleStorage.put(name, ByteSequence(byteOut.internalBuffer, 0, byteOut.size()))
  }

  override fun rename(oldName: String, newName: String) {
    moduleStorage.get(oldName)?.let {
      moduleStorage.remove(oldName)
      moduleStorage.put(newName, it)
    }
  }
}