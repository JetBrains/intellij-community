// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CrcUtils")

package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.util.zip.CRC32

fun Document.calculateCrc(project: Project, file: VirtualFile): Long = this.calculateCrc(project, null, file)

fun Document.calculateCrc(project: Project, systemId: ProjectSystemId?, file: VirtualFile): Long {
  file.getCachedCrc(modificationStamp)?.let { return it }
  return findOrCalculateCrc(modificationStamp) {
    doCalculateCrc(project, systemId, file) ?: file.calculateCrc()
  }
}

fun VirtualFile.calculateCrc(project: Project) = calculateCrc(project, null)

fun VirtualFile.calculateCrc(project: Project, systemId: ProjectSystemId?) =
  findOrCalculateCrc(modificationStamp) {
    doCalculateCrc(project, systemId) ?: calculateCrc()
  }

fun VirtualFile.calculateCrc(): Long {
  val crc32 = CRC32()
  crc32.update(contentsToByteArray())
  return crc32.value
}

private fun <T : UserDataHolder> T.findOrCalculateCrc(modificationStamp: Long, calculate: () -> Long): Long {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  val cachedCrc = getCachedCrc(modificationStamp)
  if (cachedCrc != null) return cachedCrc
  val crc = try {
    calculate()
  }
  catch (ex: IOException) {
    LOG.warn(ex)
    0
  }
  setCachedCrc(crc, modificationStamp)
  return crc
}

private fun calculateCrc(project: Project, charSequence: CharSequence, systemId: ProjectSystemId?, file: VirtualFile): Long? {
  val crcCalculator = getCrcCalculator(systemId, file)
  return crcCalculator.calculateCrc(project, file, charSequence)
}

private fun getCrcCalculator(systemId: ProjectSystemId?, file: VirtualFile): ExternalSystemCrcCalculator {
  if (systemId != null) {
    return ExternalSystemCrcCalculator.getInstance(systemId, file) ?: DefaultCrcCalculator
  }
  return DefaultCrcCalculator
}

private fun Document.doCalculateCrc(project: Project, systemId: ProjectSystemId?, file: VirtualFile) =
  when {
    file.fileType.isBinary -> null
    else -> calculateCrc(project, immutableCharSequence, systemId, file)
  }

private fun VirtualFile.doCalculateCrc(project: Project, systemId: ProjectSystemId?) =
  when {
    isDirectory -> null
    fileType.isBinary -> null
    else -> calculateCrc(project, LoadTextUtil.loadText(this), systemId, this)
  }

private fun UserDataHolder.getCachedCrc(modificationStamp: Long): Long? {
  val (value, stamp) = getUserData(CRC_CACHE) ?: return null
  if (stamp == modificationStamp) return value
  return null
}

private fun UserDataHolder.setCachedCrc(value: Long, modificationStamp: Long) {
  putUserData(CRC_CACHE, CrcCache(value, modificationStamp))
}

private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.util.CRC")

private val CRC_CACHE = Key<CrcCache>("com.intellij.openapi.externalSystem.util.CRC_CACHE")

private data class CrcCache(val value: Long, val modificationStamp: Long)
