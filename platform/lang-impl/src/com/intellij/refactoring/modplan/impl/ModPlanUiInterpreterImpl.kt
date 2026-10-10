// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.ModPlanUiInterpreter
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

/** The [ModPlanUiInterpreter] of the IDE: the questions go through [ModPlanFlowUi]. */
@ApiStatus.Internal
class ModPlanUiInterpreterImpl : ModPlanUiInterpreter {
  @RequiresEdt
  override fun <T> interpret(
    title: @NlsContexts.DialogTitle String,
    request: ModPlanRequest,
    chain: Function<ModPlanChainBuilder, ModPlanChain<T>>,
  ): ModPlanChain.ChainResult.Finished<T>? {
    val project = request.project()
    var chainResult = inBackground(project, title) { ModPlanChain.runToQuestions(request, chain) } ?: return null
    while (true) {
      val askingResult = when (val currentResult = chainResult) {
        is ModPlanChain.ChainResult.Finished -> return currentResult
        is ModPlanChain.ChainResult.Asked -> currentResult
      }
      val answers = ModPlanFlowUi.getInstance().ask(project, title, askingResult.question, askingResult.answers) ?: return null
      val readStarted = AtomicBoolean()
      chainResult = inBackground(project, title) {
        if (readStarted.getAndSet(true)) {
          // A write action interrupted the chain after it may have read PSI that changed: the chain starts again.
          ModPlanChain.runToQuestions(ModPlanChain.withOptionValues(request, answers), chain)
        }
        else {
          ModPlanChain.runAfterAnswers(request, chain, askingResult, answers)
        }
      } ?: return null
    }
  }
}

/**
 * @return the result of [compute], run in a background read action under a modal progress; a write action restarts it. Null if the user
 * cancelled the progress, which ends the whole refactoring: nothing above this entry point needs the cancellation.
 *
 * Under a read lock of the current thread, the progress of the legacy flow shares the lock with its thread, so [compute] still runs in
 * the background. Under a write action, [compute] runs at once on the current thread, with no progress and no cancel.
 */
internal fun <T : Any> inBackground(project: Project, title: @Nls String, compute: () -> T): T? {
  val application = ApplicationManager.getApplication()
  if (application.isWriteAccessAllowed) {
    return compute()
  }
  if (application.holdsReadLock()) {
    // The modal progress of coroutines is not allowed under a read lock.
    var result: T? = null
    val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      { result = runReadActionBlocking(compute) }, title, true, project)
    return result.takeIf { completed }
  }
  return try {
    runWithModalProgressBlocking(project, title) { readAction { compute() } }
  }
  catch (_: CancellationException) {
    null
  }
}
