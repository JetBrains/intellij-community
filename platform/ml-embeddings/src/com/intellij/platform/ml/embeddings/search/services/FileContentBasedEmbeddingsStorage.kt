// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope

abstract class FileContentBasedEmbeddingsStorage<T : IndexableEntity>(project: Project, cs: CoroutineScope)
  : DiskSynchronizedEmbeddingsStorage<T>(project, cs), Disposable {
  abstract fun traversePsiFile(file: PsiFile): List<T>

  protected suspend fun collectEntities(fileChangeListener: SemanticSearchFileContentChangeListener<T>): List<T> {
    val psiManager = PsiManager.getInstance(project)
    // It's important that we do not block write actions here:
    // If the write action is invoked, the read action is restarted
    return readAction {
      buildList {
        ProjectRootManager.getInstance(project).contentSourceRoots.forEach { root ->
          VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
            ProgressManager.checkCanceled()
            if (virtualFile.canonicalFile != null && virtualFile.isFile) {
              psiManager.findFile(virtualFile)?.also { file ->
                val classes = traversePsiFile(file)
                fileChangeListener.addEntityCountsForFile(virtualFile, classes)
                addAll(classes)
              }
            }
            return@iterateChildrenRecursively true
          }
        }
      }
    }
  }

  override fun dispose() {}
}