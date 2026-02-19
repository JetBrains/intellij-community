// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point (`com.intellij.multiverse.codeInsightContextProvider`) for registering [CodeInsightContext]s
 */
@ApiStatus.OverrideOnly
interface CodeInsightContextProvider {
  @RequiresReadLock
  @RequiresBackgroundThread
  fun getContexts(file: VirtualFile, project: Project): List<CodeInsightContext>

  fun invalidationRequestFlow(project: Project): Flow<Unit>

  @ApiStatus.NonExtendable
  fun interface Invalidator {
    suspend fun requestInvalidation()
  }
}