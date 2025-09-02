// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dump.paths

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.FilePropertyPusher
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.indexing.IndexedHashesSupport
import com.intellij.util.indexing.SubstitutedFileType
import java.util.*

object IndexedFilePaths {

  private const val TOO_LARGE_FILE = "<TOO LARGE>"

  private const val FAILED_TO_LOAD = "<FAILED TO LOAD: %s>"

  fun createIndexedFilePath(fileOrDir: VirtualFile, project: Project): IndexedFilePath {
    val fileId = FileBasedIndex.getFileId(fileOrDir)
    val fileUrl = fileOrDir.url
    val fileType = if (fileOrDir.isDirectory) null else fileOrDir.fileType.name
    val substitutedFileType = if (fileOrDir.isDirectory) {
      null
    }
    else {
      runReadAction {
        SubstitutedFileType.substituteFileType(fileOrDir, fileOrDir.fileType, project).name.takeIf { it != fileType }
      }
    }
    val (contentHash, indexedHash) = try {
      val fileContent = FileContentImpl.createByFile(fileOrDir) as FileContentImpl
      val encoder = Base64.getEncoder()
      runReadAction {
        val contentHash = IndexedHashesSupport.getBinaryContentHash(fileContent.content)
        val indexedHash = IndexedHashesSupport.calculateIndexedHash(fileContent, contentHash)
        encoder.encodeToString(contentHash) to encoder.encodeToString(indexedHash)
      }
    }
    catch (e: FileTooBigException) {
      TOO_LARGE_FILE to TOO_LARGE_FILE
    }
    catch (e: Throwable) {
      val msg = FAILED_TO_LOAD.format(e.message)
      msg to msg
    }

    val fileSize = if (fileOrDir.isDirectory) null else fileOrDir.length
    val portableFilePath = PortableFilePaths.getPortableFilePath(fileOrDir, project)
    val allPusherValues = runReadAction {
      dumpFilePropertyPusherValues(fileOrDir, project).mapValues { it.value?.toString() ?: "<null-value>" }
    }
    return IndexedFilePath(
      fileId,
      fileType,
      substitutedFileType,
      fileSize,
      fileUrl,
      portableFilePath,
      allPusherValues,
      contentHash = contentHash,
      indexedFileHash = indexedHash
    )
  }

  private fun dumpFilePropertyPusherValues(file: VirtualFile, project: Project): Map<String, Any?> {
    val map = hashMapOf<String, Any?>()
    FilePropertyPusher.EP_NAME.forEachExtensionSafe { pusher ->
      if (file.isDirectory && pusher.acceptsDirectory(file, project)
          || !file.isDirectory && pusher.acceptsFile(file, project)
      ) {
        map[pusher.pusherName] = pusher.getImmediateValue(project, file)
      }
    }
    return map
  }

  private val FilePropertyPusher<*>.pusherName: String
    get() = javaClass.name
      .removePrefix("com.")
      .removePrefix("intellij.")
      .removePrefix("jetbrains.")
      .replace("util.", "")
      .replace("impl.", "")
}