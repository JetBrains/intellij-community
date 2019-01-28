// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.debugOrInfoIfTestMode
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.LineSeparator
import com.intellij.util.io.inputStreamSkippingBom
import com.intellij.util.io.readCharSequence
import com.intellij.util.io.systemIndependentPath
import org.jdom.Element
import org.jdom.JDOMException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

open class FileBasedStorage(file: Path,
                            fileSpec: String,
                            rootElementName: String?,
                            pathMacroManager: PathMacroSubstitutor? = null,
                            roamingType: RoamingType? = null,
                            provider: StreamProvider? = null) :
  XmlElementStorage(fileSpec, rootElementName, pathMacroManager, roamingType, provider) {

  @Volatile private var cachedVirtualFile: VirtualFile? = null

  protected var lineSeparator: LineSeparator? = null
  protected var isBlockSavingTheContent = false

  @Volatile
  var file = file
    private set

  init {
    val app = ApplicationManager.getApplication()
    if (app != null && app.isUnitTestMode && file.toString().startsWith('$')) {
      throw AssertionError("It seems like some macros were not expanded for path: $file")
    }
  }

  protected open val isUseXmlProlog = false
  override val isUseVfsForWrite = true

  private val isUseUnixLineSeparator: Boolean
    // only ApplicationStore doesn't use xml prolog
    get() = !isUseXmlProlog

  // we never set io file to null
  fun setFile(virtualFile: VirtualFile?, ioFileIfChanged: Path?) {
    cachedVirtualFile = virtualFile
    if (ioFileIfChanged != null) {
      file = ioFileIfChanged
    }
  }

  override fun createSaveSession(states: StateMap) = FileSaveSession(states, this)

  protected open class FileSaveSession(storageData: StateMap, storage: FileBasedStorage) :
    XmlElementStorage.XmlElementStorageSaveSession<FileBasedStorage>(storageData, storage) {

    final override fun isSaveAllowed(): Boolean {
      if (!super.isSaveAllowed()) {
        return false
      }

      if (storage.isBlockSavingTheContent) {
        LOG.info("Save blocked for ${storage.fileSpec}")
        return false
      }

      return true
    }

    override fun saveLocally(dataWriter: DataWriter?) {
      var lineSeparator = storage.lineSeparator
      if (lineSeparator == null) {
        lineSeparator = if (storage.isUseUnixLineSeparator) LineSeparator.LF else LineSeparator.getSystemLineSeparator()
        storage.lineSeparator = lineSeparator
      }

      val isUseVfs = storage.isUseVfsForWrite
      val virtualFile = if (isUseVfs) storage.virtualFile else null
      if (dataWriter == null) {
        if (isUseVfs && virtualFile == null) {
          LOG.warn("Cannot find virtual file $virtualFile")
        }

        deleteFile(storage.file, this, virtualFile)
        storage.cachedVirtualFile = null
      }
      else if (isUseVfs) {
        storage.cachedVirtualFile = writeFile(storage.file, this, virtualFile, dataWriter, lineSeparator, storage.isUseXmlProlog)
      }
      else {
        val file = storage.file
        LOG.debugOrInfoIfTestMode { "Save $file" }
        try {
          dataWriter.writeTo(file, lineSeparator.separatorString)
        }
        catch (e: Throwable) {
          throw RuntimeException("Cannot write ${file}", e)
        }
      }
    }
  }

  val virtualFile: VirtualFile?
    get() {
      var result = cachedVirtualFile
      if (result == null) {
        result = LocalFileSystem.getInstance().findFileByPath(file.systemIndependentPath)
        // otherwise virtualFile.contentsToByteArray() will query expensive FileTypeManager.getInstance()).getByFile()
        result?.charset = StandardCharsets.UTF_8
        cachedVirtualFile = result
      }
      return cachedVirtualFile
    }

  protected inline fun <T> runAndHandleExceptions(task: () -> T): T? {
    try {
      return task()
    }
    catch (e: JDOMException) {
      processReadException(e)
    }
    catch (e: IOException) {
      processReadException(e)
    }
    return null
  }

  fun preloadStorageData(isEmpty: Boolean) {
    if (isEmpty) {
      storageDataRef.set(StateMap.EMPTY)
    }
    else {
      getStorageData()
    }
  }

  override fun loadLocalData(): Element? {
    isBlockSavingTheContent = false
    return runAndHandleExceptions { loadLocalDataUsingIo() }
  }

  private fun loadLocalDataUsingIo(): Element? {
    val attributes: BasicFileAttributes?
    try {
      attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
    }
    catch (e: NoSuchFileException) {
      LOG.debug { "Document was not loaded for $fileSpec, doesn't exist" }
      return null
    }

    if (!attributes.isRegularFile) {
      LOG.debug { "Document was not loaded for $fileSpec, not a file" }
      return null
    }
    else if (attributes.size() == 0L) {
      processReadException(null)
      return null
    }

    if (isUseUnixLineSeparator) {
      // do not load the whole data into memory if no need to detect line separator
      lineSeparator = LineSeparator.LF
      return JDOMUtil.load(file.inputStreamSkippingBom().reader())
    }
    else {
      val data = file.inputStreamSkippingBom().reader().readCharSequence(attributes.size().toInt())
      lineSeparator = detectLineSeparators(data, if (isUseXmlProlog) null else LineSeparator.LF)
      return JDOMUtil.load(data)
    }
  }

  protected fun processReadException(e: Exception?) {
    val contentTruncated = e == null

    isBlockSavingTheContent = !contentTruncated &&
      (PROJECT_FILE == fileSpec || fileSpec.startsWith(PROJECT_CONFIG_DIR) ||
       fileSpec == StoragePathMacros.MODULE_FILE || fileSpec == StoragePathMacros.WORKSPACE_FILE)

    if (e != null) {
      LOG.warn(e)
    }

    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode && !app.isHeadlessEnvironment) {
      val reason = if (contentTruncated) "content truncated" else e!!.message
      val action = if (isBlockSavingTheContent) "Please correct the file content" else "File content will be recreated"
      Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                   "Load Settings",
                   "Cannot load settings from file '$file': $reason\n$action",
                   NotificationType.WARNING)
        .notify(null)
    }
  }

  override fun toString() = file.systemIndependentPath
}

internal fun writeFile(file: Path?,
                       requestor: Any,
                       virtualFile: VirtualFile?,
                       dataWriter: DataWriter,
                       lineSeparator: LineSeparator,
                       prependXmlProlog: Boolean): VirtualFile {
  val result = if (file != null && (virtualFile == null || !virtualFile.isValid)) {
    getOrCreateVirtualFile(file, requestor)
  }
  else {
    virtualFile!!
  }

  if ((LOG.isDebugEnabled || ApplicationManager.getApplication().isUnitTestMode) && !FileUtilRt.isTooLarge(result.length)) {
    val content = dataWriter.toBufferExposingByteArray(lineSeparator)
    if (isEqualContent(result, lineSeparator, content, prependXmlProlog)) {
      val contentString = content.toByteArray().toString(StandardCharsets.UTF_8)
      LOG.warn("Content equals, but it must be handled not on this level: file ${result.name}, content:\n$contentString")
    }
    else if (DEBUG_LOG != null && ApplicationManager.getApplication().isUnitTestMode) {
      DEBUG_LOG = "${result.path}:\n$content\nOld Content:\n${LoadTextUtil.loadText(result)}"
    }
  }

  doWrite(requestor, result, dataWriter, lineSeparator, prependXmlProlog)
  return result
}

internal val XML_PROLOG = """<?xml version="1.0" encoding="UTF-8"?>""".toByteArray()

private fun isEqualContent(result: VirtualFile,
                           lineSeparator: LineSeparator,
                           content: BufferExposingByteArrayOutputStream,
                           prependXmlProlog: Boolean): Boolean {
  val headerLength = if (!prependXmlProlog) 0 else XML_PROLOG.size + lineSeparator.separatorBytes.size
  if (result.length.toInt() != (headerLength + content.size())) {
    return false
  }

  val oldContent = result.contentsToByteArray()

  if (prependXmlProlog && (!ArrayUtil.startsWith(oldContent, XML_PROLOG) ||
                           !ArrayUtil.startsWith(oldContent, XML_PROLOG.size, lineSeparator.separatorBytes))) {
    return false
  }

  return (headerLength until oldContent.size).all { oldContent[it] == content.internalBuffer[it - headerLength] }
}

private fun doWrite(requestor: Any, file: VirtualFile, dataWriterOrByteArray: Any, lineSeparator: LineSeparator, prependXmlProlog: Boolean) {
  LOG.debugOrInfoIfTestMode { "Save ${file.presentableUrl}" }

  if (!file.isWritable) {
    // may be element is not long-lived, so, we must write it to byte array
    val byteArray = when (dataWriterOrByteArray) {
      is DataWriter -> dataWriterOrByteArray.toBufferExposingByteArray(lineSeparator)
      else -> dataWriterOrByteArray as BufferExposingByteArrayOutputStream
    }
    throw ReadOnlyModificationException(file, object : SaveSession {
      override fun save() {
        doWrite(requestor, file, byteArray, lineSeparator, prependXmlProlog)
      }
    })
  }

  runUndoTransparentWriteAction {
    file.getOutputStream(requestor).use { output ->
      if (prependXmlProlog) {
        output.write(XML_PROLOG)
        output.write(lineSeparator.separatorBytes)
      }
      if (dataWriterOrByteArray is DataWriter) {
        dataWriterOrByteArray.write(output, lineSeparator.separatorString)
      }
      else {
        (dataWriterOrByteArray as BufferExposingByteArrayOutputStream).writeTo(output)
      }
    }
  }
}

internal fun detectLineSeparators(chars: CharSequence, defaultSeparator: LineSeparator? = null): LineSeparator {
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

private fun deleteFile(file: Path, requestor: Any, virtualFile: VirtualFile?) {
  if (virtualFile == null) {
    try {
      Files.delete(file)
    }
    catch (ignored: NoSuchFileException) {
    }
  }
  else if (virtualFile.exists()) {
    if (virtualFile.isWritable) {
      deleteFile(requestor, virtualFile)
    }
    else {
      throw ReadOnlyModificationException(virtualFile, object : SaveSession {
        override fun save() {
          // caller must wraps into undo transparent and write action
          virtualFile.delete(requestor)
        }
      })
    }
  }
}

internal fun deleteFile(requestor: Any, virtualFile: VirtualFile) {
  runUndoTransparentWriteAction { virtualFile.delete(requestor) }
}

internal class ReadOnlyModificationException(val file: VirtualFile, val session: SaveSession?) : RuntimeException("File is read-only: $file")