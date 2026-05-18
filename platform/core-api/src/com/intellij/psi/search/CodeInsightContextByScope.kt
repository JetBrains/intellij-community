// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CodeInsightContextByScope")

package com.intellij.psi.search

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger

/**
 * @return the single code insight context for the given file which is contained in the given scope
 */
@ApiStatus.Internal
fun VirtualFile.getCodeInsightContextScopeFile(project: Project, scope: GlobalSearchScope?): CodeInsightContext {
  if (scope == null || !isSharedSourceSupportEnabled(project)) {
    return anyContext()
  }

  when (val fileInfo = scope.getFileContextInfo(this)) {
    is ActualContextFileInfo -> {
      val contexts = fileInfo.contexts
      if (contexts.size > 1) {
        reportMultipleContextsAreNotSupported(this, scope, contexts)
      }
      return contexts.iterator().next()
    }
    is NoContextFileInfo -> {
      return anyContext()
    }
    else -> {
      log.error("Provided scope does not contain file ${this}, scope = $scope")
      return anyContext()
    }
  }
}

private fun reportMultipleContextsAreNotSupported(
  file: VirtualFile,
  scope: GlobalSearchScope,
  contexts: Collection<CodeInsightContext>,
) {
  if (ourContextErrorCounter.get() < MAX_CONTEXT_ERROR_NUMBER) {
    // todo IJPL-339 we need to process the file twice in this case. Not supported yet
    log.warn("Multiple contexts for file $file in scope $scope. Contexts: $contexts")
    ourContextErrorCounter.incrementAndGet()
  }
}

private val log = fileLogger()

private val ourContextErrorCounter = AtomicInteger(0)
private const val MAX_CONTEXT_ERROR_NUMBER = 10
