// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.impl

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.findElement
import com.intellij.refactoring.RefactoringUsageCollector
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanExecutionResult
import com.intellij.refactoring.modplan.ModPlanExecutor
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.ModPlanUiInterpreter
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageTarget
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * The interactive flow of a refactoring on the ModPlan engine, in the steps of the legacy flow: the questions, the search for usages, the
 * conflicts and the write. It is the same for every [ModPlanRefactoring]; its UI is [ModPlanFlowUi], and the questions are the steps of the
 * chain of the refactoring, which [ModPlanUiInterpreter] asks, see [run].
 *
 * The questions and the search for usages run in a background read action under a modal progress that the user can cancel, as in the
 * legacy flow, and a write action restarts them. Everything that PSI gives to the EDT is read there, so the EDT only shows and writes: the
 * write runs on the EDT, with no progress, in one command; its undo step is global unless the refactoring was started in an editor.
 *
 * Non-code usages in comments, strings and non-code files do not block the refactoring. They are listed in the conflicts that the user
 * confirms. With no conflicts, they are shown in a preview where the user chooses to go on. The legacy safe delete shows them in the same
 * way.
 *
 * A refusal [ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED] can also come after the questions. The cause is an element that a question
 * adds, or a usage that the engine cannot edit. Then the flow returns [Result.LEGACY], and the legacy flow asks its questions and searches
 * the usages again.
 */
@ApiStatus.Internal
object ModPlanFlow {
  enum class Result {
    /** The legacy flow has to run, also after the questions, see the KDoc of [ModPlanFlow]. */
    LEGACY,

    /** The refactoring was written. */
    DONE,

    /** Nothing was written: the user cancelled, or a preview waits for the user to go on. */
    CANCELLED,

    /** The refactoring was refused or failed, and the user was told. */
    FAILED,
  }

  /**
   * Runs [refactoring] for [request] if the flow handles it.
   *
   * @param interactive whether the user is asked: the questions of the chain, each where the chain needs its answer. Otherwise the options
   * of the request answer every question
   * @param onApplied runs after the write, on the EDT
   */
  @RequiresEdt
  fun run(refactoring: ModPlanRefactoring, request: ModPlanRequest, interactive: Boolean, onApplied: () -> Unit): Result {
    val project = request.project()
    val ui = ModPlanFlowUi.getInstance()
    // An element in a jar, a binary or a compiled file is not supported, so what is left to check before the questions is that the
    // files of the targets can be written.
    val askedFiles = runReadActionBlocking {
      if (refactoring.isSupported(request)) targetsOf(request)?.let { filesOf(it) } else null
    } ?: return Result.LEGACY
    if (!ReadonlyStatusHandler.ensureFilesWritable(project, *askedFiles.toTypedArray())) return Result.CANCELLED

    val run = FlowRun(refactoring, request, interactive, ui, askedFiles, onApplied)
    val planned = run.plan(request) ?: return Result.CANCELLED
    return run.proceed(planned, previewNonCodeUsages = true)
  }

  /**
   * The plan, and everything of it that the EDT shows, read in the read action that made the plan. It holds no [PsiElement]: the elements
   * are behind smart pointers, as the EDT reads them after the read action.
   */
  private sealed interface Planned {
    class Refused(val refusedPlan: ModPlan.RefusedPlan) : Planned

    /**
     * @property targets the targets of the request, for the preview
     * @property conflicts the messages of the conflicts by the element they are about, if the user has to confirm them
     * @property nonCodeUsages the non-code usages of the preview
     * @property files the files that the plan writes
     */
    class Ready(
      val plan: ModPlan.AcceptedPlan,
      val targets: Array<out UsageTarget>,
      val conflicts: MultiMap<SmartPsiElementPointer<PsiElement>, String>?,
      val nonCodeUsages: Array<UsageInfo>,
      val files: List<VirtualFile>,
    ) : Planned

    companion object {
      @RequiresReadLock
      fun of(plan: ModPlan, project: Project, request: ModPlanRequest): Planned {
        val acceptedPlan = when (plan) {
          is ModPlan.RefusedPlan -> return Refused(plan)
          is ModPlan.AcceptedPlan -> plan
        }
        val targets = targetsOf(request).orEmpty()
        val conflicts = if (acceptedPlan.conflicts.needsConfirmation()) {
          conflictsOf(targets.firstOrNull(), acceptedPlan)
        }
        else {
          null
        }
        val files = acceptedPlan.execution.affectedFiles
        return Ready(acceptedPlan, PsiElement2UsageTargetAdapter.convert(targets.toTypedArray(), true), conflicts,
                     nonCodeUsages(acceptedPlan), files)
      }
    }
  }

  /**
   * One run of [refactoring] for [request] in the flow, from the questions to the write.
   *
   * @property askedFiles the files that the user already made writable, which are not asked again
   */
  private class FlowRun(
    val refactoring: ModPlanRefactoring,
    val request: ModPlanRequest,
    val interactive: Boolean,
    val ui: ModPlanFlowUi,
    val askedFiles: Set<VirtualFile>,
    val onApplied: () -> Unit,
  ) {
    val project: Project = request.project()

    /**
     * Runs the chain of the refactoring from [answeredRequest], the request of the caller or the one with the answers of an earlier run of
     * the chain: what it answers is not asked again.
     *
     * @return the plan with the answers, and everything of it that the EDT shows; null if the user cancelled
     */
    @RequiresEdt
    fun plan(answeredRequest: ModPlanRequest): PlannedRun? {
      val title = refactoring.title()
      if (!interactive) {
        val planned = inBackground(project, title) { Planned.of(refactoring.plan(answeredRequest), project, request) } ?: return null
        return PlannedRun(planned, answeredRequest.optionValues())
      }
      val finished = ModPlanUiInterpreter.getInstance().interpret(title, answeredRequest, refactoring::chain) ?: return null
      // The step of the plan runs here, after every question: the search and the edits that the user waits for.
      val searchStart = System.currentTimeMillis()
      val planned = inBackground(project, title) { Planned.of(finished.value.run(ModPlanChain.withOptionValues(request, finished.answers)), project, request) }
      RefactoringUsageCollector.USAGES_SEARCHED.log(refactoring.javaClass, planned == null, System.currentTimeMillis() - searchStart)
      return PlannedRun(planned ?: return null, finished.answers)
    }

    /**
     * The steps after the questions: the conflicts, the preview of the non-code usages, and the write of the plan.
     *
     * @param previewNonCodeUsages whether the non-code usages that the refactoring leaves are shown in a preview before the write. The
     * preview writes the plan it shows, and a rerun runs the chain again with the answers of this run
     */
    @RequiresEdt
    fun proceed(plannedRun: PlannedRun, previewNonCodeUsages: Boolean): Result {
      val readyOutcome = when (val plannedOutcome = plannedRun.planned) {
        is Planned.Refused -> {
          if (plannedOutcome.refusedPlan.reason == ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED) return Result.LEGACY
          // An element that the questions added, such as a base method of a library, is refused with the message of the legacy flow.
          val element = plannedOutcome.refusedPlan.location?.let { location -> runReadActionBlocking { location.findElement() } }
          if (element == null || CommonRefactoringUtil.checkReadOnlyStatus(project, element)) ui.showError(project, refactoring)
          return Result.FAILED
        }
        is Planned.Ready -> plannedOutcome
      }
      if (readyOutcome.conflicts != null) {
        // The conflicts dialog takes the elements themselves, so they are read from the pointers right before it.
        val conflicts = runReadActionBlocking { elementsOf(readyOutcome.conflicts, request) }
        val data = RefactoringEventData()
        data.putUserData(RefactoringEventData.CONFLICTS_KEY, conflicts.values())
        project.messageBus.syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).conflictsDetected(refactoring.id(), data)
        if (!ui.confirmConflicts(project, conflicts)) return Result.CANCELLED
      }
      // The conflicts that the user accepted already listed the non-code usages, so nothing is previewed after them.
      if (previewNonCodeUsages && readyOutcome.conflicts == null &&
          ui.previewNonCodeUsages(project, refactoring, readyOutcome.plan.execution.commandName, readyOutcome.targets,
                                  readyOutcome.nonCodeUsages) { previewAgain ->
            if (previewAgain) {
              plan(ModPlanChain.withOptionValues(request, plannedRun.answers))?.let { proceed(it, previewNonCodeUsages = true) }
            }
            else {
              proceed(plannedRun, previewNonCodeUsages = false)
            }
          }) {
        return Result.CANCELLED
      }
      val files = readyOutcome.files.filter { it !in askedFiles }
      if (files.isNotEmpty() && !ReadonlyStatusHandler.ensureFilesWritable(project, *files.toTypedArray())) return Result.CANCELLED
      // A file that changed since the plan makes the execution stale, and then nothing is written, as for a file that cannot be written.
      val executeStart = System.currentTimeMillis()
      val executionResult = ModPlanExecutor.execute(readyOutcome.plan.execution, isGlobalUndo())
      RefactoringUsageCollector.EXECUTED.log(refactoring.javaClass, System.currentTimeMillis() - executeStart)
      return when (executionResult) {
        is ModPlanExecutionResult.Applied -> {
          onApplied()
          Result.DONE
        }
        is ModPlanExecutionResult.Stale, is ModPlanExecutionResult.ReadOnly, is ModPlanExecutionResult.Failed -> {
          ui.showError(project, refactoring)
          Result.FAILED
        }
      }
    }
  }

  /** The plan of a run as the EDT shows it, and the answers of the run. */
  private class PlannedRun(val planned: Planned, val answers: ModPlanOptionValues)

  /** The non-code usages of [acceptedPlan] as the usages of the usage view. */
  @VisibleForTesting
  @RequiresReadLock
  fun nonCodeUsages(acceptedPlan: ModPlan.AcceptedPlan): Array<UsageInfo> =
    acceptedPlan.conflicts.nonCodeUsages().mapNotNull { nonCodeUsage ->
      val location = nonCodeUsage.location ?: return@mapNotNull null
      val file = location.element ?: return@mapNotNull null
      val range = location.range ?: return@mapNotNull null
      if (range.startOffset == 0 && range.endOffset == file.textLength) {
        UsageInfo(file, true)
      }
      else {
        UsageInfo(file, range.startOffset, range.endOffset, true)
      }
    }.toTypedArray()

  /** The messages of the conflicts by the element they are about, or [firstTarget] if the conflict has no place. */
  @RequiresReadLock
  private fun conflictsOf(
    firstTarget: PsiElement?,
    acceptedPlan: ModPlan.AcceptedPlan,
  ): MultiMap<SmartPsiElementPointer<PsiElement>, String> {
    val messages = MultiMap<SmartPsiElementPointer<PsiElement>, String>()
    for (conflict in acceptedPlan.conflicts.conflicts) {
      val element = conflict.location?.findElement() ?: firstTarget ?: continue
      messages.putValues(SmartPointerManager.createPointer(element), conflict.messages)
    }
    return messages
  }

  /** The messages of [conflicts] by the live element; a conflict whose element is gone goes to the first target. */
  @RequiresReadLock
  private fun elementsOf(conflicts: MultiMap<SmartPsiElementPointer<PsiElement>, String>, request: ModPlanRequest): MultiMap<PsiElement, String> {
    val firstTarget = targetsOf(request)?.firstOrNull()
    val messages = MultiMap<PsiElement, String>()
    for ((pointer, values) in conflicts.entrySet()) {
      val element = pointer.element ?: firstTarget ?: continue
      messages.putValues(element, values)
    }
    return messages
  }

  /** @return the elements of the subjects of [request]; null if any of them is gone */
  @RequiresReadLock
  private fun targetsOf(request: ModPlanRequest): List<PsiElement>? = request.subjects().map { it.element() ?: return null }

  /** The files that [CommonRefactoringUtil.checkReadOnlyStatusRecursively] makes writable for [elements]. */
  @RequiresReadLock
  private fun filesOf(elements: Collection<PsiElement>): Set<VirtualFile> {
    val files = LinkedHashSet<VirtualFile>()
    for (element in elements) {
      if (element is PsiDirectory) {
        CommonRefactoringUtil.collectReadOnlyFiles(element.virtualFile, files)
      }
      else {
        element.containingFile?.virtualFile?.let { files.add(it) }
      }
    }
    return files
  }

  /** As in the legacy flow: an undo step of an action started in an editor is local to it, and needs no confirmation. */
  @Suppress("DEPRECATION")
  private fun isGlobalUndo(): Boolean = CommonDataKeys.EDITOR.getData(DataManager.getInstance().dataContext) == null
}
