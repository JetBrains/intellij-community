// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dump

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.indexing.*
import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePath
import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePaths
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePaths
import kotlin.streams.asSequence

object IndexContentDiagnosticDumper {

  fun getIndexContentDiagnostic(project: Project, indicator: ProgressIndicator): IndexContentDiagnostic {
    val providers = (FileBasedIndex.getInstance() as FileBasedIndexImpl).getOrderedIndexableFilesProviders(project)
    val visitedFiles = ConcurrentBitSet()

    indicator.text = IndexingBundle.message("index.content.diagnostic.dumping")
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    val indexedFilePaths = arrayListOf<IndexedFilePath>()
    val providerNameToOriginalFileIds = hashMapOf<String, MutableSet<Int>>()
    val filesFromUnsupportedFileSystem = arrayListOf<IndexedFilePath>()

    for ((index, provider) in providers.withIndex()) {
      indicator.text2 = provider.debugName
      val providerFileIds = hashSetOf<Int>()
      providerNameToOriginalFileIds[provider.debugName] = providerFileIds
      provider.iterateFiles(project, { fileOrDir ->
        if (!fileOrDir.isDirectory) {
          val indexedFilePath = IndexedFilePaths.createIndexedFilePath(fileOrDir, project)
          if (PortableFilePaths.isSupportedFileSystem(fileOrDir)) {
            indexedFilePaths += indexedFilePath
            providerFileIds += indexedFilePath.originalFileSystemId
          }
          else {
            // TODO: consider not excluding any file systems.
            filesFromUnsupportedFileSystem += indexedFilePath
            return@iterateFiles true
          }
        }
        true
      }, visitedFiles)
      indicator.fraction = (index + 1).toDouble() / providers.size
    }
    return IndexContentDiagnostic(
      indexedFilePaths,
      filesFromUnsupportedFileSystem,
      providerNameToOriginalFileIds
    )
  }

  fun doesFileHaveProvidedIndex(file: VirtualFile, extension: FileBasedIndexExtension<*, *>, project: Project): Boolean {
    val fileId = FileBasedIndex.getFileId(file)
    return FileBasedIndexInfrastructureExtension.EP_NAME.extensions().asSequence()
      .mapNotNull { it.createFileIndexingStatusProcessor(project) }
      .any { it.hasIndexForFile(file, fileId, extension) }
  }

}