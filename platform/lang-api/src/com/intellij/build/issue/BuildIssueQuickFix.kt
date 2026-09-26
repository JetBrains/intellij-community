// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.issue

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
interface BuildIssueQuickFix {
  val id: String

  fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> = CompletableFuture.completedFuture(null)
}
