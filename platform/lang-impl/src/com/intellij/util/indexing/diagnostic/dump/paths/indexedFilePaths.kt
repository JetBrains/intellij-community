// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dump.paths

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.FilePropertyPusher
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.SubstitutedFileType

object IndexedFilePaths {

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
    val fileSize = if (fileOrDir.isDirectory) null else fileOrDir.length
    val portableFilePath = PortableFilePaths.getPortableFilePath(fileOrDir, project)
    val resolvedFile = PortableFilePaths.findFileByPath(portableFilePath, project)
    val allPusherValues = dumpFilePropertyPusherValues(fileOrDir, project).mapValues { it.value?.toString() ?: "<null-value>" }
    val indexedFilePath = IndexedFilePath(
      fileId,
      fileType,
      substitutedFileType,
      fileSize,
      fileUrl,
      portableFilePath,
      allPusherValues
    )
    check(fileUrl == resolvedFile?.url) {
      buildString {
        appendln("File cannot be resolved")
        appendln("Original URL: $fileUrl")
        appendln("Resolved URL: ${resolvedFile?.url}")
        appendln(indexedFilePath.toString())
      }
    }
    return indexedFilePath
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