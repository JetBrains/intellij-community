// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.issue.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
class ReimportQuickFix(private val myProjectPath: String, private val systemId: ProjectSystemId) : BuildIssueQuickFix {

  override val id: String = "reimport"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    return ExternalSystemUtil.requestImport(project, myProjectPath, systemId)
  }
}
