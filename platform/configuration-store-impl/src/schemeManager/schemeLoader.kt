// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.configurationStore.schemeManager

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.configurationStore.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.options.NonLazySchemeProcessor
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.xml.dom.createXmlStreamReader
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import kotlin.io.path.invariantSeparatorsPathString

internal class SchemeLoader<T : Scheme, MUTABLE_SCHEME : T>(
  private val schemeManager: SchemeManagerImpl<T, MUTABLE_SCHEME>,
  private val oldList: SchemeCollection<T>,
  private val preScheduledFilesToDelete: MutableSet<String>,
  private val isDuringLoad: Boolean,
) {
  private val filesToDelete = HashSet<String>()
  private val schemes: MutableList<T> = oldList.list.toMutableList()
  private var newSchemesOffset = schemes.size
  // the scheme could be changed - so, hashcode will be changed - we must use identity hashing strategy
  private val schemeToInfo = IdentityHashMap(oldList.schemeToInfo)
  private val isApplied = AtomicBoolean()
  private var digest: HashStream64? = null

  // or from current session, or from current state
  private fun getInfoForExistingScheme(existingScheme: T): ExternalInfo? {
    return schemeToInfo.get(existingScheme) ?: schemeManager.schemeListManager.getExternalInfo(existingScheme)
  }

  private fun isFromFileWithNewExtension(existingScheme: T, fileNameWithoutExtension: String): Boolean {
    return getInfoForExistingScheme(existingScheme)?.fileNameWithoutExtension == fileNameWithoutExtension
  }

  /**
   * Returns list of newly added schemes.
   */
  fun apply(): List<T> {
    LOG.assertTrue(isApplied.compareAndSet(false, true))
    if (!filesToDelete.isEmpty() || !preScheduledFilesToDelete.isEmpty()) {
      LOG.debug {
        "Schedule to delete: ${filesToDelete.joinToString()} (and preScheduledFilesToDelete: ${preScheduledFilesToDelete.joinToString()})"
      }
      schemeManager.filesToDelete += filesToDelete
      schemeManager.filesToDelete += preScheduledFilesToDelete
    }

    val newSchemes = schemes.subList(newSchemesOffset, schemes.size)
    schemeManager.schemeListManager.replaceSchemeList(oldList, newList = toSchemeCollection(schemes, schemeToInfo))
    if (!isDuringLoad) {
      for (newScheme in newSchemes) {
        @Suppress("UNCHECKED_CAST")
        schemeManager.processor.onSchemeAdded(newScheme as MUTABLE_SCHEME)
      }
    }
    return newSchemes
  }

  private fun getHashStream(): HashStream64 {
    var result = digest
    if (result == null) {
      result = Hashing.komihash5_0().hashStream()!!
      digest = result
    }
    else {
      result.reset()
    }
    return result
  }

  private fun checkExisting(schemeKey: String, fileName: String, fileNameWithoutExtension: String, extension: String): Boolean {
    val processor = schemeManager.processor
    // schemes load session doesn't care about any scheme that added after session creation,
    // e.g. for now, on apply, simply current manager list replaced atomically to the new one
    // if later it will lead to some issues, this check should be done as merge operation (again, currently on apply old list is replaced and not merged)
    val existingSchemeIndex = schemes.indexOfFirst { processor.getSchemeKey(it) == schemeKey }
    val existingScheme = (if (existingSchemeIndex == -1) null else schemes.get(existingSchemeIndex)) ?: return true
    if (schemeManager.schemeListManager.readOnlyExternalizableSchemes.get(processor.getSchemeKey(existingScheme)) === existingScheme) {
      // so, a bundled scheme is shadowed
      schemes.removeAt(existingSchemeIndex)
      if (existingSchemeIndex < newSchemesOffset) {
        newSchemesOffset--
      }
      // not added to filesToDelete because it is only shadowed
      return true
    }

    if (processor.isExternalizable(existingScheme)) {
      val existingInfo = getInfoForExistingScheme(existingScheme)
      // is from file with an old extension
      if (existingInfo != null && schemeManager.schemeExtension != existingInfo.fileExtension) {
        schemeToInfo.remove(existingScheme)
        existingInfo.scheduleDelete(filesToDelete, "from file with old extension")

        schemes.removeAt(existingSchemeIndex)
        if (existingSchemeIndex < newSchemesOffset) {
          newSchemesOffset--
        }

        // when an existing loaded scheme removed, we need to remove it from schemeManager.schemeToInfo,
        // but SchemeManager will correctly remove info on save, no need to complicate
        return true
      }
    }

    if (schemeManager.schemeExtension != extension && isFromFileWithNewExtension(existingScheme, fileNameWithoutExtension)) {
      // 1.oldExt is loading after 1.newExt - we should delete 1.oldExt
      LOG.debug { "Schedule to delete: $fileName (reason: extension mismatch)" }
      filesToDelete.add(fileName)
    }
    else {
      // We don't load a scheme with a duplicated name - if we generate a unique name for it, it will be saved then with a new name.
      // It is not what all can expect.
      // Such a situation in most cases indicates an error on previous level, so we just warn about it.
      LOG.warn("Scheme file \"$fileName\" is not loaded because defines duplicated name \"$schemeKey\"")
    }
    return false
  }

  fun loadScheme(fileName: String, input: InputStream?, preloadedBytes: ByteArray?): MUTABLE_SCHEME? {
    val extension = schemeManager.getFileExtension(fileName = fileName, isAllowAny = false)
    if (isFileScheduledForDeleteInThisLoadSession(fileName)) {
      LOG.warn("Scheme file \"$fileName\" is not loaded because marked to delete")
      return null
    }

    val processor = schemeManager.processor
    val fileNameWithoutExtension = fileName.substring(0, fileName.length - extension.length)

    fun createInfo(schemeKey: String, element: Element?): ExternalInfo {
      val info = ExternalInfo(fileNameWithoutExtension = fileNameWithoutExtension, fileExtension = extension)
      if (element != null) {
        val hashStream = getHashStream()
        hashElement(element, hashStream)
        info.digest = hashStream.asLong
      }
      info.schemeKey = schemeKey
      return info
    }

    var scheme: MUTABLE_SCHEME? = null
    if (processor is LazySchemeProcessor) {
      val bytes = preloadedBytes ?: input!!.readAllBytes()
      lazyPreloadScheme(bytes, schemeManager.isOldSchemeNaming) { name, parser ->
        val attributeProvider: (String) -> String? = {
          if (parser.eventType == XMLStreamConstants.START_ELEMENT) {
            parser.getAttributeValue(null, it)
          }
          else {
            null
          }
        }
        val schemeKey = name
                        ?: processor.getSchemeKey(attributeProvider, fileNameWithoutExtension)
                        ?: throw nameIsMissed(bytes)
        if (!checkExisting(schemeKey = schemeKey, fileName = fileName, fileNameWithoutExtension = fileNameWithoutExtension, extension = extension)) {
          return null
        }

        val externalInfo = createInfo(schemeKey = schemeKey, element = null)
        val dataHolder = SchemeDataHolderImpl(processor = processor, bytes = bytes, externalInfo = externalInfo)
        val newScheme = processor.createScheme(dataHolder = dataHolder, name = schemeKey, attributeProvider = attributeProvider)
        schemeToInfo.put(newScheme, externalInfo)
        retainProbablyScheduledForDeleteFile(fileName)
        scheme = newScheme
      }
    }
    else {
      val element = if (preloadedBytes == null) JDOMUtil.load(input) else JDOMUtil.load(preloadedBytes)
      val loadedScheme = (processor as NonLazySchemeProcessor).readScheme(element, isDuringLoad) ?: return null
      val schemeKey = processor.getSchemeKey(loadedScheme)
      if (!checkExisting(schemeKey, fileName, fileNameWithoutExtension, extension)) {
        return null
      }
      schemeToInfo.put(loadedScheme, createInfo(schemeKey, element))
      retainProbablyScheduledForDeleteFile(fileName)
      scheme = loadedScheme
    }
    schemes.add(scheme!!)
    return scheme
  }

  private fun isFileScheduledForDeleteInThisLoadSession(fileName: String): Boolean = filesToDelete.contains(fileName)

  private fun retainProbablyScheduledForDeleteFile(fileName: String) {
    filesToDelete.remove(fileName)
    preScheduledFilesToDelete.remove(fileName)
  }

  fun removeUpdatedScheme(changedScheme: MUTABLE_SCHEME) {
    val index = ContainerUtil.indexOfIdentity(schemes, changedScheme)
    if (LOG.assertTrue(index >= 0)) {
      schemes.removeAt(index)
      schemeToInfo.remove(changedScheme)
    }
  }
}

internal inline fun <T> lazyPreloadScheme(bytes: ByteArray, isOldSchemeNaming: Boolean, consumer: (name: String?, parser: XMLStreamReader) -> T?): T? {
  val reader = createXmlStreamReader(bytes)
  return consumer(readSchemeNameFromXml(isOldSchemeNaming, parser = reader), reader)
}

private fun readSchemeNameFromXml(isOldSchemeNaming: Boolean, parser: XMLStreamReader): String? {
  var eventType = parser.eventType

  fun findName(): String? {
    eventType = parser.next()
    while (eventType != XMLStreamConstants.END_DOCUMENT) {
      when (eventType) {
        XMLStreamConstants.START_ELEMENT -> {
          if (parser.localName == "option" && parser.getAttributeValue(null, "name") == "myName") {
            return parser.getAttributeValue(null, "value")
          }
        }
      }

      eventType = parser.next()
    }
    return null
  }

  do {
    when (eventType) {
      XMLStreamConstants.START_ELEMENT -> {
        if (!isOldSchemeNaming || parser.localName != "component") {
          if (parser.localName == "profile" || (isOldSchemeNaming && parser.localName == "copyright")) {
            return findName()
          }
          else if (parser.localName == "inspections") {
            // backward compatibility - we don't write PROFILE_NAME_TAG anymore
            return parser.getAttributeValue(null, "profile_name") ?: findName()
          }
          else if (parser.localName == "configuration") {
            // run configuration
            return parser.getAttributeValue(null, "name")
          }
          else {
            return null
          }
        }
      }
    }
    eventType = parser.next()
  }
  while (eventType != XMLStreamConstants.END_DOCUMENT)
  return null
}

internal class ExternalInfo(@JvmField var fileNameWithoutExtension: String, @JvmField var fileExtension: String?) {
  // we keep it to detect rename
  var schemeKey: String? = null

  var digest: Long? = null

  val fileName: String
    get() = "$fileNameWithoutExtension$fileExtension"

  fun setFileNameWithoutExtension(nameWithoutExtension: String, extension: String) {
    fileNameWithoutExtension = nameWithoutExtension
    fileExtension = extension
  }

  fun isDigestEquals(newDigest: Long): Boolean = digest == newDigest

  fun scheduleDelete(filesToDelete: MutableSet<String>, @NonNls reason: String) {
    LOG.debug { "Schedule to delete: $fileName (reason: $reason)" }
    filesToDelete.add(fileName)
  }

  override fun toString(): String = fileName
}

internal fun VirtualFile.getOrCreateChild(requestor: StorageManagerFileWriteRequestor, fileName: String, directory: Boolean): VirtualFile {
  return findChild(fileName) ?: runAsWriteActionIfNeeded {
    if (directory) createChildDirectory(requestor, fileName) else createChildData(requestor, fileName)
  }
}

internal fun createDir(ioDir: Path, requestor: StorageManagerFileWriteRequestor): VirtualFile {
  NioFiles.createDirectories(ioDir)
  val parentFile = ioDir.parent
  val parentVirtualFile =
    (if (parentFile == null) null else VfsUtil.createDirectoryIfMissing(parentFile.invariantSeparatorsPathString))
    ?: throw IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile))
  return parentVirtualFile.getOrCreateChild(requestor, ioDir.fileName.toString(), directory = true)
}
