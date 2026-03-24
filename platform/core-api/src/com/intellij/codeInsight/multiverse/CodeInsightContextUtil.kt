// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:JvmName("CodeInsightContextUtil")

package com.intellij.codeInsight.multiverse

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

val FileViewProvider.codeInsightContext: CodeInsightContext
  get() {
    if (!isSharedSourceSupportEnabled(manager.project)) return defaultContext()

    val manager = CodeInsightContextManager.getInstance(this.manager.project)
    return manager.getCodeInsightContext(this)
  }

  /*internal set(newValue) {
    if (newValue == DefaultContext) {
      this.putUserData(codeInsightContextKey, null)
    }
    else {
      this.putUserData(codeInsightContextKey, newValue)
    }
  }*/

val PsiFile.codeInsightContext: CodeInsightContext
  get() = this.viewProvider.codeInsightContext

val PsiElement.codeInsightContext: CodeInsightContext
  get() = this.containingFile.codeInsightContext

fun List<FileViewProvider>.isEventSystemEnabled(): Boolean {
  val size = this.size
  if (size == 0) return false

  val first = first()
  val value = first.isEventSystemEnabled
  if (size == 1) return value

  for (provider in this) {
    if (!provider.isEventSystemEnabled) {
      log.error("files with multiple file providers must have event system enabled")
      return false
    }
  }

  return value
}

/**
 * @return true if `context` is still relevant for the `file`. It's relevant if [)][CodeInsightContextManager.getCodeInsightContexts]
 * contain `context` or if `context` is `default` or `any`.
 */
@RequiresReadLock
fun isContextRelevant(file: VirtualFile, context: CodeInsightContext, project: Project): Boolean {
  if (!isSharedSourceSupportEnabled(project)) {
    return true
  }

  if (context === anyContext() || file is LightVirtualFile) {
    return true
  }

  val contexts = CodeInsightContextManager.getInstance(project).getCodeInsightContexts(file)
  return contexts.contains(context)
}

private val log = fileLogger()

