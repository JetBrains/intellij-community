// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.issue

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
  fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*>
}
