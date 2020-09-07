// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dump

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePath
import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePaths
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePaths
import java.nio.file.Path
import java.util.*

class IndexContentDiagnosticBuilder(private val project: Project) {

  val allIndexedFilePaths = arrayListOf<IndexedFilePath>()
  val filesFromUnsupportedFileSystems = arrayListOf<IndexedFilePath>()
  val projectIndexedFileProviderDebugNameToFileIds = hashMapOf<String, MutableSet<Int>>()

  fun addFile(file: VirtualFile, providerName: String) {
    if (file.isDirectory) {
      return
    }
    val indexedFilePath = IndexedFilePaths.createIndexedFilePath(file, project)
    if (PortableFilePaths.isSupportedFileSystem(file)) {
      allIndexedFilePaths += indexedFilePath
      projectIndexedFileProviderDebugNameToFileIds.getOrPut(providerName) { TreeSet() } += indexedFilePath.originalFileSystemId
    }
    else {
      // TODO: consider not excluding any file systems.
      filesFromUnsupportedFileSystems += indexedFilePath
    }
  }

  fun build(): IndexContentDiagnostic = IndexContentDiagnostic(
    allIndexedFilePaths,
    filesFromUnsupportedFileSystems,
    projectIndexedFileProviderDebugNameToFileIds
  )
}

object IndexContentDiagnosticDumper {

  private val jacksonObjectMapper = jacksonObjectMapper()

  fun getIndexContentDiagnosticForProject(project: Project, indicator: ProgressIndicator): IndexContentDiagnostic {
    val providers = (FileBasedIndex.getInstance() as FileBasedIndexImpl).getOrderedIndexableFilesProviders(project)
    val visitedFiles = ConcurrentBitSet()

    indicator.text = IndexingBundle.message("index.content.diagnostic.dumping")
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    val builder = IndexContentDiagnosticBuilder(project)
    for ((index, provider) in providers.withIndex()) {
      indicator.text2 = provider.debugName
      provider.iterateFiles(project, { fileOrDir ->
        builder.addFile(fileOrDir, provider.debugName)
        true
      }, visitedFiles)
      indicator.fraction = (index + 1).toDouble() / providers.size
    }
    return builder.build()
  }

  fun writeTo(destination: Path, contentDiagnostic: IndexContentDiagnostic) {
    jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), contentDiagnostic)
  }

  fun readFrom(file: Path): IndexContentDiagnostic = jacksonObjectMapper.readValue(file.toFile())
}