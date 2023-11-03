// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.platform.diagnostic.telemetry.helpers.runWithSpan
import com.intellij.platform.ml.embeddings.search.utils.SEMANTIC_SEARCH_TRACER
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

abstract class FileContentBasedEmbeddingsStorage<T : IndexableEntity>(project: Project, cs: CoroutineScope)
  : DiskSynchronizedEmbeddingsStorage<T>(project, cs), Disposable {
  abstract fun traversePsiFile(file: PsiFile): Flow<T>

  override suspend fun getIndexableEntities(files: Iterable<VirtualFile>?): ScanResult<T> {
    val psiManager = PsiManager.getInstance(project)
    val filesCount = AtomicInteger(0)
    val filesScanFinish = Channel<Unit>()
    val flow = channelFlow {
      fun processFile(file: VirtualFile) {
        if (file.isFile && file.isValid && file.isInLocalFileSystem) {
          filesCount.incrementAndGet()
          launch {
            val entities = readAction {
              traversePsiFile(psiManager.findFile(file) ?: return@readAction emptyFlow())
            }
            filesScanFinish.receiveCatching()
            send(entities)
          }
        }
      }

      files?.forEach { processFile(it) } ?: runWithSpan(SEMANTIC_SEARCH_TRACER, spanIndexName + "Scanning") {
        ProjectRootManager.getInstance(project).contentSourceRoots.forEach { root ->
          VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
            processFile(file)
            return@iterateChildrenRecursively true
          }
        }
      }
      filesScanFinish.close()
    }
    return ScanResult(flow, filesCount, filesScanFinish)
  }

  override fun dispose() {}
}