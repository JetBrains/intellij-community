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
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.StorageUtil
import com.intellij.openapi.components.store.ReadOnlyModificationException
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.LineSeparator
import com.intellij.util.loadElement
import org.jdom.Element
import org.jdom.JDOMException
import org.jdom.Parent
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer

open class FileBasedStorage(file: File,
                            fileSpec: String,
                            rootElementName: String,
                            pathMacroManager: TrackingPathMacroSubstitutor? = null,
                            roamingType: RoamingType? = null,
                            provider: StreamProvider? = null) : XmlElementStorage(fileSpec, rootElementName, pathMacroManager, roamingType, provider) {
  private @Volatile var cachedVirtualFile: VirtualFile? = null
  private var lineSeparator: LineSeparator? = null
  private var blockSavingTheContent = false

  @Volatile var file = file
    private set

  init {
    if (ApplicationManager.getApplication().isUnitTestMode && file.path.startsWith('$')) {
      throw AssertionError("It seems like some macros were not expanded for path: $file")
    }
  }

  protected open val isUseXmlProlog: Boolean = false

  // we never set io file to null
  fun setFile(virtualFile: VirtualFile?, ioFileIfChanged: File?) {
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
        deleteFile(storage.file, this, virtualFile)
        storage.cachedVirtualFile = null
      }
      else {
        storage.cachedVirtualFile = writeFile(storage.file, this, virtualFile, element, if (storage.isUseXmlProlog) storage.lineSeparator!! else LineSeparator.LF, storage.isUseXmlProlog)
      }
    }
  }

  fun getVirtualFile(): VirtualFile? {
    var result = cachedVirtualFile
    if (result == null) {
      result = LocalFileSystem.getInstance().findFileByIoFile(file)
      cachedVirtualFile = result
    }
    return cachedVirtualFile
  }

  override fun loadLocalData(): Element? {
    blockSavingTheContent = false
    try {
      val file = getVirtualFile()
      if (file == null || file.isDirectory || !file.isValid) {
        LOG.debug { "Document was not loaded for $fileSpec file is ${if (file == null) "null" else "directory"}" }
      }
      else if (file.length == 0L) {
        processReadException(null)
      }
      else {
        val charBuffer = CharsetToolkit.UTF8_CHARSET.decode(ByteBuffer.wrap(file.contentsToByteArray()))
        lineSeparator = detectLineSeparators(charBuffer, if (isUseXmlProlog) null else LineSeparator.LF)
        return loadElement(charBuffer)
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
    blockSavingTheContent = !contentTruncated && (PROJECT_FILE == fileSpec || fileSpec.startsWith(PROJECT_CONFIG_DIR) || fileSpec == StoragePathMacros.MODULE_FILE || fileSpec == StoragePathMacros.WORKSPACE_FILE)
    if (!ApplicationManager.getApplication().isUnitTestMode && !ApplicationManager.getApplication().isHeadlessEnvironment) {
      if (e != null) {
        LOG.info(e)
      }
      Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
        "Load Settings",
        "Cannot load settings from file '$file': ${if (contentTruncated) "content truncated" else e!!.message}\n${if (blockSavingTheContent) "Please correct the file content" else "File content will be recreated"}",
        NotificationType.WARNING)
        .notify(null)
    }
  }

  override fun toString() = file.systemIndependentPath
}

fun writeFile(file: File?, requestor: Any, virtualFile: VirtualFile?, element: Element, lineSeparator: LineSeparator, prependXmlProlog: Boolean): VirtualFile {
  val result = if (file != null && (virtualFile == null || !virtualFile.isValid)) {
    StorageUtil.getOrCreateVirtualFile(requestor, file)
  }
  else {
    virtualFile!!
  }

  if (LOG.isDebugEnabled || ApplicationManager.getApplication().isUnitTestMode) {
    val content = element.toBufferExposingByteArray(lineSeparator.separatorString)
    if (isEqualContent(result, lineSeparator, content, prependXmlProlog)) {
      throw IllegalStateException("Content equals, but it must be handled not on this level: ${result.name}")
    }
    else if (StorageUtil.DEBUG_LOG != null && ApplicationManager.getApplication().isUnitTestMode) {
      StorageUtil.DEBUG_LOG = "${result.path}:\n$content\nOld Content:\n${LoadTextUtil.loadText(result)}\n---------"
    }
  }

  doWrite(requestor, result, element, lineSeparator, prependXmlProlog)
  return result
}

private val XML_PROLOG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>".toByteArray()

private fun isEqualContent(result: VirtualFile, lineSeparator: LineSeparator, content: BufferExposingByteArrayOutputStream, prependXmlProlog: Boolean): Boolean {
  val headerLength = if (!prependXmlProlog) 0 else XML_PROLOG.size + lineSeparator.separatorBytes.size
  if (result.length.toInt() != (headerLength + content.size())) {
    return false
  }

  val oldContent = result.contentsToByteArray()

  if (prependXmlProlog && (!ArrayUtil.startsWith(oldContent, XML_PROLOG) || !ArrayUtil.startsWith(oldContent, XML_PROLOG.size, lineSeparator.separatorBytes))) {
    return false
  }

  for (i in headerLength..oldContent.size - 1) {
    if (oldContent[i] != content.internalBuffer[i - headerLength]) {
      return false
    }
  }
  return true
}

private fun doWrite(requestor: Any, file: VirtualFile, content: Any, lineSeparator: LineSeparator, prependXmlProlog: Boolean) {
  LOG.debug { "Save ${file.presentableUrl}" }
  runWriteAction {  ->
    try {
      val out = file.getOutputStream(requestor)
      try {
        if (prependXmlProlog) {
          out.write(XML_PROLOG)
          out.write(lineSeparator.separatorBytes)
        }
        if (content is Element) {
          JDOMUtil.writeParent(content, out, lineSeparator.separatorString)
        }
        else {
          (content as BufferExposingByteArrayOutputStream).writeTo(out)
        }
      }
      finally {
        out.close()
      }
    }
    catch (e: FileNotFoundException) {
      // may be element is not long-lived, so, we must write it to byte array
      val byteArray = if (content is Element) content.toBufferExposingByteArray(lineSeparator.separatorString) else (content as BufferExposingByteArrayOutputStream)
      throw ReadOnlyModificationException(file, e, StateStorage.SaveSession { doWrite(requestor, file, byteArray, lineSeparator, prependXmlProlog) })
    }
  }
}

internal fun Parent.toBufferExposingByteArray(lineSeparator: String = "\n"): BufferExposingByteArrayOutputStream {
  val out = BufferExposingByteArrayOutputStream(512)
  JDOMUtil.writeParent(this, out, lineSeparator)
  return out
}

internal fun detectLineSeparators(chars: CharSequence, defaultSeparator: LineSeparator?): LineSeparator {
  for (c in chars) {
    if (c == '\r') {
      return LineSeparator.CRLF
    }
    else if (c == '\n') {
      // if we are here, there was no \r before
      return LineSeparator.LF
    }
  }
  return defaultSeparator ?: LineSeparator.getSystemLineSeparator()
}

private fun deleteFile(file: File, requestor: Any, virtualFile: VirtualFile?) {
  if (virtualFile == null) {
    LOG.warn("Cannot find virtual file ${file.absolutePath}")
  }

  if (virtualFile == null) {
    if (file.exists()) {
      FileUtil.delete(file)
    }
  }
  else if (virtualFile.exists()) {
    try {
      deleteFile(requestor, virtualFile)
    }
    catch (e: FileNotFoundException) {
      throw ReadOnlyModificationException(virtualFile, e, StateStorage.SaveSession { deleteFile(requestor, virtualFile) })
    }
  }
}

fun deleteFile(requestor: Any, virtualFile: VirtualFile) {
  runWriteAction { virtualFile.delete(requestor) }
}