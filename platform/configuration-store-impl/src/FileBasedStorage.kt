/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.configurationStore

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.FileStorage
import com.intellij.openapi.components.impl.stores.StateMap
import com.intellij.openapi.components.impl.stores.StorageUtil
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.LineSeparator
import org.jdom.Element
import org.jdom.JDOMException
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

open class FileBasedStorage(private volatile var file: File,
                            fileSpec: String,
                            rootElementName: String,
                            pathMacroManager: TrackingPathMacroSubstitutor? = null,
                            roamingType: RoamingType? = null,
                            provider: StreamProvider? = null) : XmlElementStorage(fileSpec, rootElementName, pathMacroManager, roamingType, provider), FileStorage {
  private volatile var cachedVirtualFile: VirtualFile? = null
  private var lineSeparator: LineSeparator? = null
  private var blockSavingTheContent = false

  init {
    if (ApplicationManager.getApplication().isUnitTestMode() && file.getPath().startsWith('$')) {
      throw AssertionError("It seems like some macros were not expanded for path: $file")
    }
  }

  protected open val isUseXmlProlog: Boolean = false

  // we never set io file to null
  override fun setFile(virtualFile: VirtualFile?, ioFileIfChanged: File?) {
    cachedVirtualFile = virtualFile
    if (ioFileIfChanged != null) {
      file = ioFileIfChanged
    }
  }

  override fun createSaveSession(states: StateMap) = FileSaveSession(states, this)

  protected open class FileSaveSession(storageData: StateMap, storage: FileBasedStorage) : XmlElementStorage.XmlElementStorageSaveSession<FileBasedStorage>(storageData, storage) {
    override fun save() {
      if (!storage.blockSavingTheContent) {
        super.save()
      }
    }

    override fun saveLocally(element: Element?) {
      if (storage.lineSeparator == null) {
        storage.lineSeparator = if (storage.isUseXmlProlog) LineSeparator.LF else LineSeparator.getSystemLineSeparator()
      }

      val virtualFile = storage.getVirtualFile()
      if (element == null) {
        StorageUtil.deleteFile(storage.file, this, virtualFile)
        storage.cachedVirtualFile = null
      }
      else {
        storage.cachedVirtualFile = StorageUtil.writeFile(storage.file, this, virtualFile, element, if (storage.isUseXmlProlog) storage.lineSeparator!! else LineSeparator.LF, storage.isUseXmlProlog)
      }
    }
  }

  override fun getVirtualFile(): VirtualFile? {
    var result = cachedVirtualFile
    if (result == null) {
      result = LocalFileSystem.getInstance().findFileByIoFile(file)
      cachedVirtualFile = result
    }
    return cachedVirtualFile
  }

  override fun getFile() = file

  override fun loadLocalData(): Element? {
    blockSavingTheContent = false
    try {
      val file = getVirtualFile()
      if (file == null || file.isDirectory() || !file.isValid()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Document was not loaded for $fileSpec file is ${if (file == null) "null" else "directory"}")
        }
      }
      else if (file.getLength() == 0L) {
        processReadException(null)
      }
      else {
        val charBuffer = CharsetToolkit.UTF8_CHARSET.decode(ByteBuffer.wrap(file.contentsToByteArray()))
        lineSeparator = StorageUtil.detectLineSeparators(charBuffer, if (isUseXmlProlog) null else LineSeparator.LF)
        return JDOMUtil.loadDocument(charBuffer).detachRootElement()
      }
    }
    catch (e: JDOMException) {
      processReadException(e)
    }
    catch (e: IOException) {
      processReadException(e)
    }
    return null
  }

  private fun processReadException(e: Exception?) {
    val contentTruncated = e == null
    blockSavingTheContent = !contentTruncated && (StorageUtil.isProjectOrModuleFile(fileSpec) || fileSpec == StoragePathMacros.WORKSPACE_FILE)
    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      if (e != null) {
        LOG.info(e)
      }
      Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Load Settings", "Cannot load settings from file '$file': ${if (contentTruncated) "content truncated" else e!!.getMessage()}\n${if (blockSavingTheContent) "Please correct the file content" else "File content will be recreated"}", NotificationType.WARNING).notify(null)
    }
  }

  override fun toString() = file.systemIndependentPath
}
