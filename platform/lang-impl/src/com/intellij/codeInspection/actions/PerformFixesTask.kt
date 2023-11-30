// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions

import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.QuickFix
import com.intellij.lang.LangBundle
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.modcommand.ModCommandExecutor.BatchExecutionResult
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import java.util.concurrent.Callable

open class PerformFixesTask(project: Project, descriptors: List<CommonProblemDescriptor>, quickFixClass: Class<*>?) :
  AbstractPerformFixesTask(project, descriptors.toTypedArray(), quickFixClass) {

  override fun <D : CommonProblemDescriptor> collectFix(fix: QuickFix<D>, descriptor: D, project: Project): BatchExecutionResult {
    if (fix is ModCommandQuickFix) {
      descriptor as ProblemDescriptor
      val command = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable<ModCommand, RuntimeException> {
            ReadAction.nonBlocking(Callable { fix.perform(myProject, descriptor) })
              .expireWhen { myProject.isDisposed }
              .executeSynchronously()
          }, LangBundle.message("apply.fixes"), true, myProject)
      if (command == null) return ModCommandExecutor.Result.ABORT
      return ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(descriptor), command)
    }
    fix.applyFix(project, descriptor)
    return ModCommandExecutor.Result.SUCCESS
  }
}