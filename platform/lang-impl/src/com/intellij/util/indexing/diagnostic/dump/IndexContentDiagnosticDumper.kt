// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dump

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePath
import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePaths
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePaths
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class IndexContentDiagnosticBuilder(private val project: Project) {
  private val allIndexedFilePaths: MutableSet<IndexedFilePath> = ContainerUtil.newConcurrentSet()
  private val filesFromUnsupportedFileSystems: MutableSet<IndexedFilePath> = ContainerUtil.newConcurrentSet()
  private val projectIndexedFileProviderDebugNameToFileIds: MutableMap<String, MutableSet<Int>> = ConcurrentHashMap<String, MutableSet<Int>>()

  fun addFile(file: VirtualFile, providerName: String) {
    if (file.isDirectory) {
      return
    }
    val indexedFilePath = IndexedFilePaths.createIndexedFilePath(file, project)
    if (PortableFilePaths.isSupportedFileSystem(file)) {
      allIndexedFilePaths += indexedFilePath
      projectIndexedFileProviderDebugNameToFileIds.getOrPut(providerName) { ContainerUtil.newConcurrentSet() } += indexedFilePath.originalFileSystemId
    }
    else {
      // TODO: consider not excluding any file systems.
      filesFromUnsupportedFileSystems += indexedFilePath
    }
  }

  fun build(): IndexContentDiagnostic = IndexContentDiagnostic(
    allIndexedFilePaths.toList(),
    filesFromUnsupportedFileSystems.toList(),
    projectIndexedFileProviderDebugNameToFileIds.mapValues { (_, ids) -> ids.toSortedSet() }
  )
}

object IndexContentDiagnosticDumper {

  private val jacksonObjectMapper = jacksonObjectMapper()

  fun getIndexContentDiagnosticForProject(project: Project, indicator: ProgressIndicator): IndexContentDiagnostic {
    val providers = (FileBasedIndex.getInstance() as FileBasedIndexImpl).getIndexableFilesProviders(project)

    indicator.text = IndexingBundle.message("index.content.diagnostic.dumping")
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    val builder = IndexContentDiagnosticBuilder(project)
    val processed = AtomicInteger()

    PushedFilePropertiesUpdaterImpl.invokeConcurrentlyIfPossible(providers.map { provider ->
      Runnable {
        indicator.text2 = provider.debugName
        provider.iterateFiles(project, { fileOrDir ->
          builder.addFile(fileOrDir, provider.debugName)
          true
        }, VirtualFileFilter.ALL)
        indicator.fraction = processed.incrementAndGet().toDouble() / providers.size
      }
    })

    return builder.build()
  }

  fun writeTo(destination: Path, contentDiagnostic: IndexContentDiagnostic) {
    jacksonObjectMapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), contentDiagnostic)
  }

  fun readFrom(file: Path): IndexContentDiagnostic = jacksonObjectMapper.readValue(file.toFile())
}