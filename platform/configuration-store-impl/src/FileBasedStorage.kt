// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.application.options.PathMacrosCollector
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.components.impl.stores.getComponentNameIfValid
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.util.SafeStAXStreamBuilder
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.LineSeparator
import com.intellij.util.xml.dom.createXmlStreamReader
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import javax.xml.stream.XMLStreamException
import kotlin.io.path.deleteIfExists

abstract class FileBasedStorage(
  file: Path,
  fileSpec: String,
  rootElementName: String?,
  pathMacroManager: PathMacroSubstitutor? = null,
  roamingType: RoamingType,
  provider: StreamProvider? = null,
) : XmlElementStorage(
  fileSpec = fileSpec,
  rootElementName = rootElementName,
  pathMacroSubstitutor = pathMacroManager,
  storageRoamingType = roamingType,
  provider = provider,
) {
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

  protected open val isUseXmlProlog: Boolean = false

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

  override fun createSaveSession(states: StateMap): FileSaveSessionProducer = FileSaveSessionProducer(storageData = states, storage = this)

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

    override fun saveLocally(dataWriter: DataWriter?) {
      var lineSeparator = storage.lineSeparator
      if (lineSeparator == null) {
        lineSeparator = if (storage.isUseUnixLineSeparator) LineSeparator.LF else LineSeparator.getSystemLineSeparator()
        storage.lineSeparator = lineSeparator
      }

      val isUseVfs = storage.isUseVfsForWrite
      val virtualFile = if (isUseVfs) storage.getVirtualFile() else null
      when {
        dataWriter == null -> {
          if (isUseVfs && virtualFile == null) {
            LOG.warn("Cannot find virtual file")
          }
          deleteFile(file = storage.file, requestor = this, virtualFile = virtualFile)
          storage.cachedVirtualFile = null
        }
        isUseVfs -> {
          storage.cachedVirtualFile = writeFile(
            cachedFile = storage.file,
            requestor = this,
            virtualFile = virtualFile,
            dataWriter = dataWriter,
            lineSeparator = lineSeparator,
            prependXmlProlog = storage.isUseXmlProlog,
          )
        }
        else -> {
          writeFile(
            file = storage.file,
            requestor = this,
            dataWriter = dataWriter,
            lineSeparator = lineSeparator,
            prependXmlProlog = storage.isUseXmlProlog,
          )
        }
      }
    }

    private fun deleteFile(file: Path, requestor: StorageManagerFileWriteRequestor, virtualFile: VirtualFile?) {
      if (virtualFile == null) {
        file.deleteIfExists()
      }
      else if (virtualFile.exists()) {
        if (virtualFile.isWritable) {
          virtualFile.delete(requestor)
        }
        else {
          throw ReadOnlyModificationException(virtualFile, object : SaveSession {
            override fun saveBlocking() {
              // the caller must wrap into undo-transparent write action
              virtualFile.delete(requestor)
            }
          })
        }
      }
    }
  }

  fun getVirtualFile(): VirtualFile? {
    var result = cachedVirtualFile
    if (result == null) {
      result = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
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
        val xmlStreamReader = createXmlStreamReader(Files.newInputStream(file))
        try {
          return SafeStAXStreamBuilder.buildNsUnawareAndClose(xmlStreamReader)
        }
        finally {
          xmlStreamReader.close()
        }
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
        (fileSpec == PROJECT_FILE || fileSpec.startsWith(PROJECT_CONFIG_DIR) ||
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
      Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                   ConfigurationStoreBundle.message("notification.load.settings.title"),
                   "${ConfigurationStoreBundle.message("notification.load.settings.content", file)}: $reason\n$action",
                   NotificationType.WARNING)
        .notify(null)
    }
  }

  override fun toString(): String = "FileBasedStorage(file=$file, fileSpec=$fileSpec, isBlockSavingTheContent=$blockSaving)"
}

internal fun writeFile(
  cachedFile: Path?,
  requestor: StorageManagerFileWriteRequestor,
  virtualFile: VirtualFile?,
  dataWriter: DataWriter,
  lineSeparator: LineSeparator,
  prependXmlProlog: Boolean
): VirtualFile {
  val file = if (cachedFile == null || virtualFile?.isValid == true) virtualFile!! else getOrCreateVirtualFile(cachedFile, requestor)

  if ((LOG.isDebugEnabled || ApplicationManager.getApplication().isUnitTestMode) && !FileUtilRt.isTooLarge(file.length)) {
    fun isEqualContent(file: VirtualFile,
                       lineSeparator: LineSeparator,
                       content: BufferExposingByteArrayOutputStream,
                       prependXmlProlog: Boolean): Boolean {
      val headerLength = if (!prependXmlProlog) 0 else XML_PROLOG.size + lineSeparator.separatorBytes.size
      if (file.length.toInt() == headerLength + content.size()) {
        val oldContent = file.contentsToByteArray()
        if (!prependXmlProlog || (ArrayUtil.startsWith(oldContent, XML_PROLOG) &&
                                  ArrayUtil.startsWith(oldContent, XML_PROLOG.size, lineSeparator.separatorBytes))) {
          return (headerLength until oldContent.size).all { oldContent[it] == content.internalBuffer[it - headerLength] }
        }
      }
      return false
    }

    val content = dataWriter.toBufferExposingByteArray(lineSeparator)
    if (isEqualContent(file = file, lineSeparator = lineSeparator, content = content, prependXmlProlog = prependXmlProlog)) {
      val contentString = content.toByteArray().toString(Charsets.UTF_8)
      val message = "Content equals, but it must be handled not at this level: file ${file.name}, content:\n${contentString}"
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

  fun doWrite(
    requestor: StorageManagerFileWriteRequestor,
    file: VirtualFile,
    dataWriterOrByteArray: Any,
    lineSeparator: LineSeparator,
    prependXmlProlog: Boolean,
  ) {
    LOG.debug { "Save ${file.presentableUrl}" }

    if (!file.isWritable) {
      // maybe the element is not long-lived, so we must write it to a byte array
      val byteArray = when (dataWriterOrByteArray) {
        is DataWriter -> dataWriterOrByteArray.toBufferExposingByteArray(lineSeparator)
        else -> dataWriterOrByteArray as BufferExposingByteArrayOutputStream
      }
      throw ReadOnlyModificationException(file, object : SaveSession {
        override fun saveBlocking() {
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
          dataWriterOrByteArray.writeTo(output, lineSeparator)
        }
        else {
          (dataWriterOrByteArray as BufferExposingByteArrayOutputStream).writeTo(output)
        }
      }
    }
  }

  doWrite(
    requestor = requestor,
    file = file,
    dataWriterOrByteArray = dataWriter,
    lineSeparator = lineSeparator,
    prependXmlProlog = prependXmlProlog,
  )

  return file
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
  catch (e: ReadOnlyModificationException) {
    throw e
  }
  catch (e: Throwable) {
    throw RuntimeException("Cannot write $file", e)
  }
}

internal class ReadOnlyModificationException(
  @JvmField val file: VirtualFile,
  @JvmField val session: SaveSession?,
) : RuntimeException("File is read-only: $file")

private fun loadDataAndDetectLineSeparator(file: Path): Pair<Element, LineSeparator?> {
  val data = Files.readAllBytes(file)
  val offset = CharsetToolkit.getBOMLength(data, StandardCharsets.UTF_8)
  val text = String(data, offset, data.size - offset, StandardCharsets.UTF_8)
  val xmlStreamReader = createXmlStreamReader(StringReader(text))
  val element = try {
    SafeStAXStreamBuilder.buildNsUnawareAndClose(xmlStreamReader)
  }
  finally {
    xmlStreamReader.close()
  }

  return Pair(element, detectLineSeparator(text))
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

@Internal
fun loadComponentsAndDetectLineSeparator(
  dir: Path,
  pathMacroSubstitutor: PathMacroSubstitutor?
): Pair<Map<String, Element>, Map<String, LineSeparator?>> {
  try {
    Files.newDirectoryStream(dir).use { files ->
      val fileToState = HashMap<String, Element>()
      val fileToSeparator = HashMap<String, LineSeparator?>()

      for (file in files) {
        // ignore system files like .DS_Store on Mac
        if (!file.toString().endsWith(ComponentStorageUtil.DEFAULT_EXT, ignoreCase = true)) {
          continue
        }

        try {
          val elementLineSeparatorPair = loadDataAndDetectLineSeparator(file)
          val element = elementLineSeparatorPair.first
          val componentName = getComponentNameIfValid(element) ?: continue
          if (element.name != ComponentStorageUtil.COMPONENT) {
            LOG.error("Incorrect root tag name (${element.name}) in $file")
            continue
          }

          val elementChildren = element.children
          if (elementChildren.isEmpty()) {
            continue
          }

          val state = elementChildren.get(0).detach()
          if (state.isEmpty) {
            continue
          }

          if (pathMacroSubstitutor != null) {
            pathMacroSubstitutor.expandPaths(state)
            if (pathMacroSubstitutor is TrackingPathMacroSubstitutor) {
              pathMacroSubstitutor.addUnknownMacros(componentName, PathMacrosCollector.getMacroNames(state))
            }
          }

          val name = file.fileName.toString()
          fileToState.put(name, state)
          fileToSeparator.put(name, elementLineSeparatorPair.second)
        }
        catch (e: Throwable) {
          if (e.message!!.startsWith("Unexpected End-of-input in prolog")) {
            LOG.warn("Ignore empty file $file")
          }
          else {
            LOG.warn("Unable to load state from $file", e)
          }
        }
      }
      return Pair(fileToState, fileToSeparator)
    }
  }
  catch (e: DirectoryIteratorException) {
    throw e.cause!!
  }
  catch (ignore: NoSuchFileException) {
    return Pair(java.util.Map.of(), java.util.Map.of())
  }
  catch (ignore: NotDirectoryException) {
    return Pair(java.util.Map.of(), java.util.Map.of())
  }
}