// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point (`com.intellij.multiverse.codeInsightContextProvider`) for registering [CodeInsightContext]s
 *
 * @see CodeInsightContextManager.registerTestOnlyCodeInsightContextProvider for testing
 */
@ApiStatus.OverrideOnly
interface CodeInsightContextProvider {
  @RequiresReadLock
  @RequiresBackgroundThread
  fun getContexts(file: VirtualFile, project: Project): List<CodeInsightContext>

  fun subscribeToChanges(project: Project, invalidator: Invalidator)

  @ApiStatus.NonExtendable
  fun interface Invalidator {
    @RequiresWriteLock
    fun requestInvalidation()
  }
}