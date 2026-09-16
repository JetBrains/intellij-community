// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CrcUtils")

package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readBytes
import com.intellij.openapi.vfs.readText
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32

@Deprecated("Use getOrCalculateCrc instead")
fun Document.calculateCrc(project: Project, file: VirtualFile): Long =
  project.service<DocumentCrcCache>().getOrCalculateCrc(ProjectSystemId.IDE, this, file) ?: 0

@Deprecated("Use getOrCalculateCrc instead")
fun Document.calculateCrc(project: Project, systemId: ProjectSystemId?, file: VirtualFile): Long =
  project.service<DocumentCrcCache>().getOrCalculateCrc(systemId ?: ProjectSystemId.IDE, this, file) ?: 0

@Deprecated("Use getOrCalculateCrc instead")
fun VirtualFile.calculateCrc(project: Project): Long =
  project.service<VirtualFileCrcCache>().getOrCalculateCrc(ProjectSystemId.IDE, this) ?: 0

@Deprecated("Use getOrCalculateCrc instead")
fun VirtualFile.calculateCrc(project: Project, systemId: ProjectSystemId?): Long =
  project.service<VirtualFileCrcCache>().getOrCalculateCrc(systemId ?: ProjectSystemId.IDE, this) ?: 0

@Deprecated("Use getOrCalculateCrc instead")
fun VirtualFile.calculateCrc(): Long = calculateCrcFromBytes(this)

/**
 * Returns the CRC of [file], from the cache or a new calculation.
 *
 * Note: Thread unsafe. Expect a cancel and a retry on each change to the file or its document.
 *
 * @return `null` when CRC cannot be calculated for [file] (e.g. [IOException])
 */
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun getOrCalculateCrc(project: Project, systemId: ProjectSystemId, file: VirtualFile): Long? {
  ProgressManager.checkCanceled()

  val document = FileDocumentManager.getInstance().getCachedDocument(file)
  return document?.let { project.service<DocumentCrcCache>().getOrCalculateCrc(systemId, it, file) }
         ?: project.service<VirtualFileCrcCache>().getOrCalculateCrc(systemId, file)
}

private fun calculateCrcFromBytes(file: VirtualFile): Long {
  val crc32 = CRC32()
  crc32.update(file.readBytes())
  return crc32.value
}

private fun calculateCrcFromText(text: CharSequence): Long {
  val crc32 = CRC32()
  for (char in text) {
    crc32.update(char.code)
  }
  return crc32.value
}

private fun calculateCrcFromText(project: Project, systemId: ProjectSystemId, file: VirtualFile, text: CharSequence): Long {
  val crcCalculator = ExternalSystemCrcCalculator.getInstance(systemId, file) ?: DefaultCrcCalculator
  return crcCalculator.calculateCrc(project, file, text) ?: calculateCrcFromText(text)
}

/**
 * File context CRC values cache.
 *
 * The VFS rejects a [CachedValue] in the user data of a [VirtualFile], because a VFS entry outlives the project.
 * A [Document] is an application level object too, but the CRC also depends on the [Project] and [ProjectSystemId].
 * Therefore, this service holds the [CachedValue], and the file or the document stays only the dependency.
 */
private abstract class CrcCache(protected val project: Project) : Disposable {

  private data class CrcKey(val systemId: ProjectSystemId, val dependency: Any)

  private val cache = ConcurrentHashMap<CrcKey, CachedValue<Long?>>()

  protected fun getOrCalculateCrc(systemId: ProjectSystemId, dependency: Any, calculate: () -> Long?): Long? {
    val cachedValue = cache.computeIfAbsent(CrcKey(systemId, dependency)) {
      CachedValuesManager.getManager(project).createCachedValue {
        CachedValueProvider.Result.create(calculateCrcOrNull(calculate), dependency)
      }
    }
    return cachedValue.value
  }

  private fun calculateCrcOrNull(calculate: () -> Long?): Long? {
    return try {
      calculate()
    }
    catch (_: IOException) {
      null
    }
  }

  override fun dispose() {
    cache.clear()
  }
}

@Service(Service.Level.PROJECT)
private class DocumentCrcCache(project: Project) : CrcCache(project) {
  fun getOrCalculateCrc(systemId: ProjectSystemId, document: Document, file: VirtualFile): Long? =
    getOrCalculateCrc(systemId, document) {
      when {
        file.fileType.isBinary -> null
        else -> calculateCrcFromText(project, systemId, file, document.immutableCharSequence)
      }
    }
}

@Service(Service.Level.PROJECT)
private class VirtualFileCrcCache(project: Project) : CrcCache(project) {
  fun getOrCalculateCrc(systemId: ProjectSystemId, file: VirtualFile): Long? =
    getOrCalculateCrc(systemId, file) {
      when {
        file.fileType.isBinary -> calculateCrcFromBytes(file)
        else -> calculateCrcFromText(project, systemId, file, file.readText())
      }
    }
}
