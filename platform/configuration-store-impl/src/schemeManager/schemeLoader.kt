// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.*
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.options.NonLazySchemeProcessor
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.systemIndependentPath
import gnu.trove.THashSet
import org.jdom.Element
import org.xmlpull.mxp1.MXParser
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

internal class SchemeLoader<T : Any, MUTABLE_SCHEME : T>(private val schemeManager: SchemeManagerImpl<T, MUTABLE_SCHEME>,
                                                         private val oldSchemes: ConcurrentList<T>,
                                                         private val preScheduledFilesToDelete: MutableSet<String>,
                                                         private val isDuringLoad: Boolean) {
  private val filesToDelete = THashSet<String>()

  private val schemes = oldSchemes.toMutableList()
  private var newSchemesOffset = schemes.size

  // scheme could be changed - so, hashcode will be changed - we must use identity hashing strategy
  private val schemeToInfo = ContainerUtil.newIdentityTroveMap<T, ExternalInfo>()

  private val isApplied = AtomicBoolean()

  private var digest: MessageDigest? = null

  // or from current session, or from current state
  private fun getInfoForExistingScheme(existingScheme: T): ExternalInfo? {
    return schemeToInfo.get(existingScheme) ?: schemeManager.schemeToInfo.get(existingScheme)
  }

  private fun isFromFileWithOldExtension(existingScheme: T): Boolean {
    val info = getInfoForExistingScheme(existingScheme)
    // scheme from file with old extension, so, we must ignore it
    return info != null && schemeManager.schemeExtension != info.fileExtension
  }

  private fun isFromFileWithNewExtension(existingScheme: T, fileNameWithoutExtension: String): Boolean {
    return getInfoForExistingScheme(existingScheme)?.fileNameWithoutExtension == fileNameWithoutExtension
  }

  /**
   * Returns list of new schemes.
   */
  fun apply(): List<T> {
    LOG.assertTrue(isApplied.compareAndSet(false, true))
    schemeManager.filesToDelete.addAll(filesToDelete)
    schemeManager.filesToDelete.addAll(preScheduledFilesToDelete)

    schemeManager.schemeToInfo.putAll(schemeToInfo)

    val result = schemes.subList(newSchemesOffset, schemes.size)
    schemeManager.schemeListManager.replaceSchemeList(oldSchemes, schemes)
    if (!isDuringLoad) {
      for (newScheme in result) {
        @Suppress("UNCHECKED_CAST")
        schemeManager.processor.onSchemeAdded(newScheme as MUTABLE_SCHEME)
      }
    }
    return result
  }

  private fun getDigest(): MessageDigest {
    var result = digest
    if (result == null) {
      result = createDataDigest()
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
      // so, bundled scheme is shadowed
      schemes.removeAt(existingSchemeIndex)
      if (existingSchemeIndex < newSchemesOffset) {
        newSchemesOffset--
      }
      // not added to filesToDelete because it is only shadowed
      return true
    }
    else if (processor.isExternalizable(existingScheme) && isFromFileWithOldExtension(existingScheme)) {
      schemes.removeAt(existingSchemeIndex)
      if (existingSchemeIndex < newSchemesOffset) {
        newSchemesOffset--
      }
      filesToDelete.add(fileName)
    }
    else {
      if (schemeManager.schemeExtension != extension && isFromFileWithNewExtension(existingScheme, fileNameWithoutExtension)) {
        // 1.oldExt is loading after 1.newExt - we should delete 1.oldExt
        filesToDelete.add(fileName)
      }
      else {
        // We don't load scheme with duplicated name - if we generate unique name for it, it will be saved then with new name.
        // It is not what all can expect. Such situation in most cases indicates error on previous level, so, we just warn about it.
        LOG.warn("Scheme file \"$fileName\" is not loaded because defines duplicated name \"$schemeKey\"")
      }
      return false
    }
    return true
  }

  fun loadScheme(fileName: String, input: InputStream): MUTABLE_SCHEME? {
    val extension = schemeManager.getFileExtension(fileName, isAllowAny = false)
    if (isFileScheduledForDeleteInThisLoadSession(fileName)) {
      LOG.warn("Scheme file \"$fileName\" is not loaded because marked to delete")
      return null
    }

    val processor = schemeManager.processor
    val fileNameWithoutExtension = fileName.substring(0, fileName.length - extension.length)

    fun createInfo(schemeName: String, element: Element?): ExternalInfo {
      val info = ExternalInfo(fileNameWithoutExtension, extension)
      if (element != null) {
        val digest = getDigest()
        serializeElementToBinary(element, DigestOutputStream(digest))
        info.digest = digest.digest()
      }
      info.schemeKey = schemeName
      return info
    }

    var scheme: MUTABLE_SCHEME? = null
    if (processor is LazySchemeProcessor) {
      val bytes = input.readBytes()
      lazyPreloadScheme(bytes, schemeManager.isOldSchemeNaming) { name, parser ->
        val attributeProvider = Function<String, String?> {
          if (parser.eventType == XmlPullParser.START_TAG) {
            parser.getAttributeValue(null, it)
          }
          else {
            null
          }
        }
        val schemeKey = name
                        ?: processor.getSchemeKey(attributeProvider, fileNameWithoutExtension)
                        ?: throw nameIsMissed(bytes)
        if (!checkExisting(schemeKey, fileName, fileNameWithoutExtension, extension)) {
          return null
        }

        val externalInfo = createInfo(schemeKey, null)
        scheme = processor.createScheme(SchemeDataHolderImpl(processor, bytes, externalInfo), schemeKey, attributeProvider)
        schemeToInfo.put(scheme, externalInfo)
        retainProbablyScheduledForDeleteFile(fileName)
      }
    }
    else {
      val element = JDOMUtil.load(input.bufferedReader())
      scheme = (processor as NonLazySchemeProcessor).readScheme(element, isDuringLoad) ?: return null
      val schemeKey = processor.getSchemeKey(scheme!!)
      if (!checkExisting(schemeKey, fileName, fileNameWithoutExtension, extension)) {
        return null
      }

      schemeToInfo.put(scheme, createInfo(schemeKey, element))
      retainProbablyScheduledForDeleteFile(fileName)
    }

    schemes.add(scheme)
    return scheme
  }

  private fun isFileScheduledForDeleteInThisLoadSession(fileName: String): Boolean {
    return filesToDelete.contains(fileName)
  }

  private fun retainProbablyScheduledForDeleteFile(fileName: String) {
    filesToDelete.remove(fileName)
    preScheduledFilesToDelete.remove(fileName)
  }
}

internal inline fun useSchemeLoader(executor: (Ref<SchemeLoader<Any, Any>>) -> Unit) {
  val schemeLoaderRef = Ref<SchemeLoader<Any, Any>>()
  executor(schemeLoaderRef)
  val schemeLoader = schemeLoaderRef.get()
  if (schemeLoader != null) {
    schemeLoaderRef.set(null)
    schemeLoader.apply()
  }
}

internal inline fun lazyPreloadScheme(bytes: ByteArray, isOldSchemeNaming: Boolean, consumer: (name: String?, parser: XmlPullParser) -> Unit) {
  val parser = MXParser()
  parser.setInput(bytes.inputStream().reader())
  consumer(preload(isOldSchemeNaming, parser), parser)
}

private fun preload(isOldSchemeNaming: Boolean, parser: MXParser): String? {
  var eventType = parser.eventType

  fun findName(): String? {
    eventType = parser.next()
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          if (parser.name == "option" && parser.getAttributeValue(null, "name") == "myName") {
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
      XmlPullParser.START_TAG -> {
        if (!isOldSchemeNaming || parser.name != "component") {
          if (parser.name == "profile" || (isOldSchemeNaming && parser.name == "copyright")) {
            return findName()
          }
          else if (parser.name == "inspections") {
            // backward compatibility - we don't write PROFILE_NAME_TAG anymore
            return parser.getAttributeValue(null, "profile_name") ?: findName()
          }
          else if (parser.name == "configuration") {
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
  while (eventType != XmlPullParser.END_DOCUMENT)
  return null
}

internal class ExternalInfo(var fileNameWithoutExtension: String, var fileExtension: String?) {
  // we keep it to detect rename
  var schemeKey: String? = null

  var digest: ByteArray? = null

  val fileName: String
    get() = "$fileNameWithoutExtension$fileExtension"

  fun setFileNameWithoutExtension(nameWithoutExtension: String, extension: String) {
    fileNameWithoutExtension = nameWithoutExtension
    fileExtension = extension
  }

  fun isDigestEquals(newDigest: ByteArray) = Arrays.equals(digest, newDigest)

  override fun toString() = fileName
}

internal fun VirtualFile.getOrCreateChild(fileName: String, requestor: Any): VirtualFile {
  return findChild(fileName) ?: runUndoTransparentWriteAction { createChildData(requestor, fileName) }
}

internal fun createDir(ioDir: Path, requestor: Any): VirtualFile {
  ioDir.createDirectories()
  val parentFile = ioDir.parent
  val parentVirtualFile = (if (parentFile == null) null else VfsUtil.createDirectoryIfMissing(parentFile.systemIndependentPath))
      ?: throw IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile))
  return parentVirtualFile.getOrCreateChild(ioDir.fileName.toString(), requestor)
}