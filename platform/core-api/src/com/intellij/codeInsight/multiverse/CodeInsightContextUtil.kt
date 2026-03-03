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
 * Ensures that `context` is still relevant for the `file`.
 * It's relevant if [CodeInsightContextManager.getCodeInsightContexts] contain `context` or if `context` is `default` or `any`.
 */
@RequiresReadLock
fun ensureContextRelevant(file: VirtualFile, context: CodeInsightContext, project: Project) {
  if (!isSharedSourceSupportEnabled(project)) return
  if (areIrrelevantContextsAllowed()) return
  if (context === anyContext() || file is LightVirtualFile) return

  val contextManager = CodeInsightContextManager.getInstance(project)
  val contexts = contextManager.getCodeInsightContexts(file)
  if (context in contexts) return

  log.error("context $context is not relevant for file $file. Existing contexts:\n  ${contexts.joinToString("\n  ")}")
}

/**
 * Suppresses the [ensureContextRelevant] assertion for the duration of [action] on the current thread.
 * Intended for internal plumbing that intentionally tolerates stale contexts (validity reanimation,
 * smart-pointer restoration during a move, etc.). Re-entrant.
 */
@ApiStatus.Internal
fun <T> withAllowedIrrelevantContexts(action: () -> T): T {
  irrelevantContextsDepth.set(irrelevantContextsDepth.get() + 1)
  try {
    return action()
  }
  finally {
    irrelevantContextsDepth.set(irrelevantContextsDepth.get() - 1)
  }
}

/**
 * Java-friendly overload of [withAllowedIrrelevantContexts] for void actions.
 */
@ApiStatus.Internal
fun runWithAllowedIrrelevantContexts(action: Runnable) {
  withAllowedIrrelevantContexts { action.run() }
}

@ApiStatus.Internal
fun areIrrelevantContextsAllowed(): Boolean = irrelevantContextsDepth.get() > 0

private val irrelevantContextsDepth: ThreadLocal<Int> = ThreadLocal.withInitial { 0 }

private val log = fileLogger()
