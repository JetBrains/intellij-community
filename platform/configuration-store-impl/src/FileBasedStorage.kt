// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SafeStAXStreamBuilder
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.LineSeparator
import com.intellij.util.io.readCharSequence
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.xml.dom.createXmlStreamReader
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import javax.xml.stream.XMLStreamException

open class FileBasedStorage(file: Path,
                            fileSpec: String,
                            rootElementName: String?,
                            pathMacroManager: PathMacroSubstitutor? = null,
                            roamingType: RoamingType,
                            provider: StreamProvider? = null) :
  XmlElementStorage(fileSpec = fileSpec,
                    rootElementName = rootElementName,
                    pathMacroSubstitutor = pathMacroManager,
                    roamingType = roamingType,
                    provider = provider) {

  @Volatile
  private var cachedVirtualFile: VirtualFile? = null

  private var lineSeparator: LineSeparator? = null
  private var blockSaving: BlockSaving? = null

  @Volatile
  var file = file
    private set

  protected open val configuration: FileBasedStorageConfiguration
    get() = defaultFileBasedStorageConfiguration

  init {
    val app = ApplicationManager.getApplication()
    if (app != null && app.isUnitTestMode && file.toString().startsWith('$')) {
      throw AssertionError("It seems like some macros were not expanded for path: $file")
    }
  }

  protected open val isUseXmlProlog = false

  final override val isUseVfsForWrite: Boolean
    get() = configuration.isUseVfsForWrite

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

  override fun createSaveSession(states: StateMap) = FileSaveSessionProducer(states, this)

  protected open class FileSaveSessionProducer(storageData: StateMap, storage: FileBasedStorage) :
    XmlElementStorageSaveSessionProducer<FileBasedStorage>(storageData, storage) {

    final override fun isSaveAllowed(): Boolean {
      if (!super.isSaveAllowed()) {
        return false
      }

      if (storage.blockSaving != null) {
        LOG.warn("Save blocked for $storage")
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

      val isUseVfs = storage.configuration.isUseVfsForWrite
      val virtualFile = if (isUseVfs) storage.getVirtualFile(StateStorageOperation.WRITE) else null
      when {
        dataWriter == null -> {
          if (isUseVfs && virtualFile == null) {
            LOG.warn("Cannot find virtual file")
          }

          deleteFile(storage.file, this, virtualFile)
          storage.cachedVirtualFile = null
        }
        isUseVfs -> {
          storage.cachedVirtualFile = writeFile(storage.file, this, virtualFile, dataWriter, lineSeparator, storage.isUseXmlProlog)
        }
        else -> {
          val file = storage.file
          LOG.debug { "Save $file" }
          try {
            dataWriter.writeTo(file, this, lineSeparator)
          }
          catch (e: ReadOnlyModificationException) {
            throw e
          }
          catch (e: Throwable) {
            throw RuntimeException("Cannot write $file", e)
          }
        }
      }
    }
  }

  fun getVirtualFile(reasonOperation: StateStorageOperation): VirtualFile? {
    var result = cachedVirtualFile
    if (result == null) {
      result = configuration.resolveVirtualFile(file.systemIndependentPath, reasonOperation)
      cachedVirtualFile = result
    }
    return result
  }

  private inline fun <T> runAndHandleExceptions(task: () -> T): T? {
    try {
      return task()
    }
    catch (e: JDOMException) {
      processReadException(e)
    }
    catch (e: XMLStreamException) {
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
    blockSaving = null
    return runAndHandleExceptions {
      if (configuration.isUseVfsForRead) {
        loadUsingVfs()
      }
      else {
        loadLocalDataUsingIo()
      }
    }
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
      val xmlStreamReader = createXmlStreamReader(Files.newInputStream(file))
      try {
        return SafeStAXStreamBuilder.build(xmlStreamReader, true, false, SafeStAXStreamBuilder.FACTORY)
      }
      finally {
        xmlStreamReader.close()
      }
    }
    else {
      val data = CharsetToolkit.inputStreamSkippingBOM(Files.newInputStream(file)).reader().readCharSequence(attributes.size().toInt())
      lineSeparator = detectLineSeparators(data, if (isUseXmlProlog) null else LineSeparator.LF)
      return JDOMUtil.load(data)
    }
  }

  private fun loadUsingVfs(): Element? {
    val virtualFile = getVirtualFile(StateStorageOperation.READ)
    if (virtualFile == null || !virtualFile.exists()) {
      // only on first load
      handleVirtualFileNotFound()
      return null
    }

    val byteArray = virtualFile.contentsToByteArray()
    if (byteArray.isEmpty()) {
      processReadException(null)
      return null
    }

    val charBuffer = Charsets.UTF_8.decode(ByteBuffer.wrap(byteArray))
    lineSeparator = detectLineSeparators(charBuffer, if (isUseXmlProlog) null else LineSeparator.LF)
    return JDOMUtil.load(charBuffer)
  }

  protected open fun handleVirtualFileNotFound() {
  }

  private fun processReadException(e: Exception?) {
    val contentTruncated = e == null

    if (!contentTruncated &&
          (fileSpec == PROJECT_FILE || fileSpec.startsWith(PROJECT_CONFIG_DIR) ||
           fileSpec == StoragePathMacros.MODULE_FILE || fileSpec == StoragePathMacros.WORKSPACE_FILE)) {
      blockSaving = BlockSaving(reason = e?.toString() ?: "empty file")
    }
    else {
      blockSaving = null
    }
    if (e != null) {
      LOG.warn("Cannot read ${toString()}", e)
    }

    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode && !app.isHeadlessEnvironment) {
      val reason = if (contentTruncated) ConfigurationStoreBundle.message("notification.load.settings.error.reason.truncated") else e!!.message
      val action = if (blockSaving == null)
        ConfigurationStoreBundle.message("notification.load.settings.action.content.will.be.recreated")
        else ConfigurationStoreBundle.message("notification.load.settings.action.please.correct.file.content")
      @Suppress("removal", "DEPRECATION")
      Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                   ConfigurationStoreBundle.message("notification.load.settings.title"),
                   "${ConfigurationStoreBundle.message("notification.load.settings.content", file)}: $reason\n$action",
                   NotificationType.WARNING)
        .notify(null)
    }
  }

  override fun toString() = "FileBasedStorage(file=$file, fileSpec=$fileSpec, isBlockSavingTheContent=$blockSaving)"
}

internal fun writeFile(cachedFile: Path?,
                       requestor: StorageManagerFileWriteRequestor,
                       virtualFile: VirtualFile?,
                       dataWriter: DataWriter,
                       lineSeparator: LineSeparator,
                       prependXmlProlog: Boolean): VirtualFile {
  val file = if (cachedFile != null && (virtualFile == null || !virtualFile.isValid)) {
    getOrCreateVirtualFile(cachedFile, requestor)
  }
  else {
    virtualFile!!
  }

  if ((LOG.isDebugEnabled || ApplicationManager.getApplication().isUnitTestMode) && !FileUtilRt.isTooLarge(file.length)) {
    val content = dataWriter.toBufferExposingByteArray(lineSeparator)
    if (isEqualContent(file, lineSeparator, content, prependXmlProlog)) {
      val contentString = content.toByteArray().toString(Charsets.UTF_8)
      val message = "Content equals, but it must be handled not on this level: file ${file.name}, content:\n$contentString"
      if (ApplicationManager.getApplication().isUnitTestMode) {
        LOG.debug(message)
      }
      else {
        LOG.warn(message)
      }
    }
    else if (DEBUG_LOG != null && ApplicationManager.getApplication().isUnitTestMode) {
      DEBUG_LOG = "${file.path}:\n$content\nOld Content:\n${LoadTextUtil.loadText(file)}"
    }
  }

  doWrite(requestor, file, dataWriter, lineSeparator, prependXmlProlog)
  return file
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

private fun doWrite(requestor: StorageManagerFileWriteRequestor, file: VirtualFile, dataWriterOrByteArray: Any, lineSeparator: LineSeparator, prependXmlProlog: Boolean) {
  LOG.debug { "Save ${file.presentableUrl}" }

  if (!file.isWritable) {
    // may be element is not long-lived, so, we must write it to a byte array
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

  runAsWriteActionIfNeeded {
    file.getOutputStream(requestor).use { output ->
      if (prependXmlProlog) {
        output.write(XML_PROLOG)
        output.write(lineSeparator.separatorBytes)
      }
      if (dataWriterOrByteArray is DataWriter) {
        dataWriterOrByteArray.write(output, lineSeparator)
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

private fun deleteFile(file: Path, requestor: StorageManagerFileWriteRequestor, virtualFile: VirtualFile?) {
  if (virtualFile == null) {
    try {
      Files.delete(file)
    }
    catch (ignored: NoSuchFileException) {
    }
  }
  else if (virtualFile.exists()) {
    if (virtualFile.isWritable) {
      virtualFile.delete(requestor)
    }
    else {
      throw ReadOnlyModificationException(virtualFile, object : SaveSession {
        override fun save() {
          // caller must wrap into undo transparent and write action
          virtualFile.delete(requestor)
        }
      })
    }
  }
}

internal class ReadOnlyModificationException(val file: VirtualFile, val session: SaveSession?) : RuntimeException("File is read-only: $file")

private data class BlockSaving(@NonNls val reason: String)