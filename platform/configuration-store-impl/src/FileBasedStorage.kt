// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.buildNsUnawareJdom
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.LineSeparator
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import javax.xml.stream.XMLStreamException

@JvmField
internal val XML_PROLOG: ByteArray = """<?xml version="1.0" encoding="UTF-8"?>""".toByteArray()

@ApiStatus.Internal
abstract class FileBasedStorage internal constructor(
  file: Path,
  fileSpec: String,
  rootElementName: String?,
  pathMacroManager: PathMacroSubstitutor? = null,
  roamingType: RoamingType,
  provider: StreamProvider? = null,
  listener: OperationListener? = null
) : XmlElementStorage(fileSpec = fileSpec, rootElementName = rootElementName, pathMacroSubstitutor = pathMacroManager, storageRoamingType = roamingType, provider = provider, listener = listener) {
  @Volatile private var cachedVirtualFile: VirtualFile? = null

  private var lineSeparator: LineSeparator? = null
  private var blockSaving: String? = null

  @Volatile var file: Path = file
    private set

  init {
    val app = ApplicationManager.getApplication()
    if (app != null && app.isUnitTestMode && file.toString().startsWith('$')) {
      throw AssertionError("It seems like some macros were not expanded for path: $file")
    }
  }

  protected open val isUseXmlProlog: Boolean
    get() = false

  // only ApplicationStore doesn't use an XML prolog
  private val isUseUnixLineSeparator: Boolean
    get() = !isUseXmlProlog

  // we never set an I/O file to null
  fun setFile(virtualFile: VirtualFile?, ioFileIfChanged: Path?) {
    cachedVirtualFile = virtualFile
    if (ioFileIfChanged != null) {
      file = ioFileIfChanged
    }
  }

  override fun createSaveSession(states: StateMap): FileSaveSessionProducer = FileSaveSessionProducer(storageData = states, storage = this)

  @ApiStatus.Internal
  protected open class FileSaveSessionProducer(storageData: StateMap, storage: FileBasedStorage) :
    XmlElementStorageSaveSessionProducer<FileBasedStorage>(originalStates = storageData, storage = storage) {

    final override fun isSaveAllowed(): Boolean {
      return when {
        !super.isSaveAllowed() -> false
        storage.blockSaving != null -> {
          LOG.warn("Save blocked for $storage")
          false
        }
        else -> true
      }
    }

    override fun remove(events: MutableList<VFileEvent>?) {
      val virtualFile = if (events == null) null else storage.getVirtualFile()
      Files.deleteIfExists(storage.file)
      storage.cachedVirtualFile = null
      if (events != null && virtualFile != null && virtualFile.isValid) {
        events.add(VFileDeleteEvent(/*requestor =*/ this, virtualFile))
      }
    }

    override fun saveLocally(dataWriter: DataWriter, events: MutableList<VFileEvent>?) {
      val lineSeparator = getOrCacheLineSeparator()

      val virtualFile = if (events == null) null else storage.getVirtualFile()
      writeFile(file = storage.file, requestor = this, dataWriter = dataWriter, lineSeparator = lineSeparator, prependXmlProlog = storage.isUseXmlProlog)
      if (events != null) {
        if (virtualFile == null) {
          VirtualFileManager.getInstance().refreshAndFindFileByNioPath(storage.file.parent)?.let { dir ->
            events.add(creationEvent(storage.file, dir))
          }
        }
        else {
          events.add(updatingEvent(storage.file, virtualFile))
        }
      }
    }

    private fun getOrCacheLineSeparator(): LineSeparator {
      var lineSeparator = storage.lineSeparator
      if (lineSeparator == null) {
        lineSeparator = if (storage.isUseUnixLineSeparator) LineSeparator.LF else LineSeparator.getSystemLineSeparator()
        storage.lineSeparator = lineSeparator
      }
      return lineSeparator
    }
  }

  fun getVirtualFile(): VirtualFile? {
    var result = cachedVirtualFile
    if (result == null) {
      result = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file)
      if (result != null && result.isValid) {
        // otherwise virtualFile.contentsToByteArray() will query expensive FileTypeManager.getInstance()).getByFile()
        result.setCharset(Charsets.UTF_8, null, false)
        cachedVirtualFile = result
      }
    }
    return result
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

    try {
      val attributes: BasicFileAttributes?
      try {
        attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
      }
      catch (_: NoSuchFileException) {
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
        // do not load the whole data into memory if there is no need to detect line separators
        lineSeparator = LineSeparator.LF
        return buildNsUnawareJdom(file)
      }
      else {
        val (element, separator) = loadDataAndDetectLineSeparator(file)
        lineSeparator = separator ?: if (isUseXmlProlog) LineSeparator.getSystemLineSeparator() else LineSeparator.LF
        return element
      }
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

  private fun processReadException(e: Exception?) {
    if (e != null &&
        (fileSpec == StoragePathMacros.PROJECT_FILE || fileSpec.startsWith(PROJECT_CONFIG_DIR) ||
         fileSpec == StoragePathMacros.MODULE_FILE || fileSpec == StoragePathMacros.WORKSPACE_FILE)) {
      blockSaving = e.toString()
    }
    else {
      blockSaving = null
    }
    if (e != null) {
      LOG.warn("Cannot read ${toString()}", e)
    }

    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode && !app.isHeadlessEnvironment) {
      val reason = if (e != null) e.message else ConfigurationStoreBundle.message("notification.load.settings.error.reason.truncated")
      val action = if (blockSaving == null)
        ConfigurationStoreBundle.message("notification.load.settings.action.content.will.be.recreated")
        else ConfigurationStoreBundle.message("notification.load.settings.action.please.correct.file.content")
      @Suppress("removal", "DEPRECATION")
      val notification = Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                                      ConfigurationStoreBundle.message("notification.load.settings.title"),
                                      "${ConfigurationStoreBundle.message("notification.load.settings.content", file)}: $reason\n$action",
                                      NotificationType.WARNING)
      app.invokeLater { notification.notify(null) }
    }
  }

  override fun toString(): String = "FileBasedStorage(file=$file, fileSpec=$fileSpec, isBlockSavingTheContent=$blockSaving)"
}

internal fun writeFile(
  file: Path,
  requestor: StorageManagerFileWriteRequestor,
  dataWriter: DataWriter,
  lineSeparator: LineSeparator,
  prependXmlProlog: Boolean
) {
  LOG.debug { "Save $file" }
  try {
    dataWriter.writeTo(file = file, requestor = requestor, lineSeparator = lineSeparator, useXmlProlog = prependXmlProlog)
  }
  catch (e: Throwable) {
    throw RuntimeException("Cannot write $file", e)
  }
}

internal fun creationEvent(file: Path, dir: VirtualFile): VFileCreateEvent {
  val attributes = FileAttributes.fromNio(file, NioFiles.readAttributes(file))
  return VFileCreateEvent(RELOADING_STORAGE_WRITE_REQUESTOR, dir, file.fileName.toString(), attributes.isDirectory, attributes, /*symlinkTarget =*/ null, /*children =*/ null)
}

internal fun updatingEvent(file: Path, vFile: VirtualFile): VFileContentChangeEvent {
  val attributes = FileAttributes.fromNio(file, NioFiles.readAttributes(file))
  return VFileContentChangeEvent(
    RELOADING_STORAGE_WRITE_REQUESTOR, vFile, vFile.modificationStamp, /*newModificationStamp =*/ -1,
    vFile.timeStamp, attributes.lastModified, vFile.length, attributes.length)
}

internal fun loadDataAndDetectLineSeparator(file: Path): Pair<Element, LineSeparator?> {
  val text = ComponentStorageUtil.loadTextContent(file)
  return buildNsUnawareJdom(StringReader(text)) to detectLineSeparator(text)
}

private fun detectLineSeparator(chars: CharSequence): LineSeparator? {
  for (element in chars) {
    if (element == '\r') {
      return LineSeparator.CRLF
    }
    // if we are here, there was no '\r' before
    if (element == '\n') {
      return LineSeparator.LF
    }
  }
  return null
}