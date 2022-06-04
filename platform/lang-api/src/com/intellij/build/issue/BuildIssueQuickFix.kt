// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.issue

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
interface BuildIssueQuickFix {
  val id: String
  @JvmDefault
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use runQuickFix(Project, DataContext) function instead.", ReplaceWith("runQuickFix(Project, DataContext)"))
  fun runQuickFix(project: Project, dataContext: DataProvider): CompletableFuture<*> = CompletableFuture.completedFuture(null)
  @JvmDefault
  fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> = CompletableFuture.completedFuture(null)
}
