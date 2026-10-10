// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete

import com.intellij.model.SymbolPointer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanConflictKind
import com.intellij.refactoring.modplan.ModPlanConflicts
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.refactoring.modplan.ModPlanFinalStep
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider.DeletionDecision
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanGenericSafeDeleteProvider
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageViewBundle

/**
 * The safe delete on the ModPlan engine: [chain] joins the chains of the providers into the chain of one request. The order is
 * [ModPlanSafeDeleteProvider.prepareRequest] for each requested subject, one after another, then for each subject they add, then
 * [ModPlanSafeDeleteProvider.analyze] for everything that is deleted, then the plan, which turns the edits and the deletions into one
 * execution.
 *
 * Each step takes the builder of the run first and returns the chain of the next provider call joined with [ModPlanChain.andThen], so the
 * questions of every provider are asks of one chain. The [ModPlanSafeDeleteProvider.DeletionDecision] of a subject adds to the deleted
 * subjects, which the engine keeps; the request of the caller stays as it is. As in the legacy safe delete, what is deleted is computed
 * once in a run; a run that starts again computes it again.
 *
 * The flow of the engine and its providers: [com.intellij.refactoring.modplan.safeDelete].
 */
@Service(Service.Level.APP)
class ModPlanSafeDeleteRefactoring : ModPlanRefactoring {
  override fun id(): String = REFACTORING_ID

  override fun title(): String = RefactoringBundle.message("safe.delete.title")

  override fun targetsNodeText(): String = RefactoringBundle.message("items.to.be.deleted")

  override fun nonCodeUsagesHeader(nonCodeUsages: Int, files: Int): String {
    return RefactoringBundle.message("safe.delete.comment.occurences.header", UsageViewBundle.getOccurencesString(nonCodeUsages, files))
  }

  /** Every subject is owned by a registered [ModPlanSafeDeleteProvider] that supports it; it searches nothing. */
  override fun isSupported(request: ModPlanRequest): Boolean {
    return super.isSupported(request) && request.subjects().all { subject ->
      ModPlanSafeDeleteProvider.EP_NAME.findFirstSafe { it.isApplicable(subject) }?.isLegacyOnly(subject) == false
    }
  }

  /** Checks the requested subjects, then starts every subject of the run. */
  override fun chain(chainBuilder: ModPlanChainBuilder): ModPlanChain<ModPlanFinalStep<ModPlan>> {
    val requestedSubjects = withAdded(emptyList(), chainBuilder.request.subjects())
    invalidSubjectRefusal(requestedSubjects)?.let { refusal -> return chainBuilder.passValue(ModPlanFinalStep { refusal }) }
    return startSubjects(chainBuilder, requestedSubjects, emptyMap())
  }

  /**
   * Starts the first of [deletedSubjects] that has no owner yet, then goes on with the subjects that its provider adds; analyzes when every
   * subject is started. A provider adds at the end, so the requested subjects start first, and an added one is not expanded again, as in
   * the legacy safe delete.
   *
   * @param deletedSubjects the subjects that are deleted so far, in the order of the start
   * @param owners          the provider of each started subject, in the order of the start
   */
  private fun startSubjects(
    chainBuilder: ModPlanChainBuilder,
    deletedSubjects: List<SymbolPointer>,
    owners: Map<SymbolPointer, ModPlanSafeDeleteProvider>,
  ): ModPlanChain<ModPlanFinalStep<ModPlan>> {
    val subject = deletedSubjects.firstOrNull { it !in owners } ?: return analysisChain(chainBuilder, deletedSubjects, owners)
    // The owner of a subject is the first provider that applies to it; a named element that no provider owns goes to the generic one.
    val owner = ModPlanSafeDeleteProvider.EP_NAME.findFirstSafe { it.isApplicable(subject) }
                ?: ModPlanGenericSafeDeleteProvider.takeIf { it.isApplicable(subject) }
                ?: return refused(chainBuilder, ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, subject)
    if (owner.isLegacyOnly(subject)) return refused(chainBuilder, ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, subject)
    return owner.prepareRequest(chainBuilder, subject, deletedSubjects).andThen { afterPreparation, decision ->
      when (decision) {
        is DeletionDecision.Refused -> refused(afterPreparation, ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, subject)
        is DeletionDecision.Deleted -> {
          startSubjects(afterPreparation, withAdded(deletedSubjects, decision.alsoDeleted), owners + (subject to owner))
        }
        // A replaced subject stays: the analysis of its replacements edits it as a usage, as in the legacy safe delete.
        is DeletionDecision.Replaced -> startSubjects(afterPreparation, withAdded(deletedSubjects - subject, decision.replacements), owners)
      }
    }
  }

  /**
   * @return [subjects], then each of [addedSubjects] whose symbol is not there yet. Subjects are different objects even for one symbol, so
   * they are compared here, when they are added, and the sets and maps of the engine work by identity, see [SymbolPointer]. The instance
   * that is there stays, so a requested subject keeps the instance of the request: a provider tells a requested subject with `contains`,
   * see [ModPlanSafeDeleteProvider.prepareRequest].
   */
  private fun withAdded(
    subjects: List<SymbolPointer>,
    addedSubjects: Collection<SymbolPointer>,
  ): List<SymbolPointer> {
    return addedSubjects.fold(subjects) { allSubjects, addedSubject ->
      if (allSubjects.any { it.isSameSymbol(addedSubject) }) { allSubjects } else { allSubjects + addedSubject }
    }
  }

  /** Analyzes every one of [deletedSubjects], one after another in the order of [owners], then creates the plan in its final step. */
  private fun analysisChain(
    chainBuilder: ModPlanChainBuilder,
    deletedSubjects: List<SymbolPointer>,
    owners: Map<SymbolPointer, ModPlanSafeDeleteProvider>,
  ): ModPlanChain<ModPlanFinalStep<ModPlan>> {
    if (deletedSubjects.any { it.element() == null }) return chainBuilder.passValue(ModPlanFinalStep { gone() })
    // An element that the expansion adds can be one of a library, such as the base method that a method of the project overrides.
    deletedSubjects.firstOrNull { ModPlanRefactoring.isInReadOnlyContainer(it.element()) }
      ?.let { return refused(chainBuilder, ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, it) }
    return analyzeSubjects(chainBuilder, owners.entries.toList(), deletedSubjects, emptyList())
  }

  /**
   * Runs the chain of the analysis of the first of [remainingSubjects] and of the ones after it, one after another, as the analysis of each
   * may ask; then ends with the final step that creates the plan from [analysisSteps] and theirs. The steps run there, after every
   * question.
   *
   * @param analysisSteps the steps of the analyses so far, with their subjects, not run
   */
  private fun analyzeSubjects(
    chainBuilder: ModPlanChainBuilder,
    remainingSubjects: List<Map.Entry<SymbolPointer, ModPlanSafeDeleteProvider>>,
    deletedSubjects: List<SymbolPointer>,
    analysisSteps: List<Pair<SymbolPointer, ModPlanFinalStep<ModPlanAnalysisResult>>>,
  ): ModPlanChain<ModPlanFinalStep<ModPlan>> {
    val (subject, owner) = remainingSubjects.firstOrNull()
                           ?: return chainBuilder.finish { finalRequest -> planOf(finalRequest, deletedSubjects, analysisSteps) }
    return owner.analyze(chainBuilder, subject, deletedSubjects).andThen { afterAnalysis, analysisStep ->
      analyzeSubjects(afterAnalysis, remainingSubjects.drop(1), deletedSubjects, analysisSteps + (subject to analysisStep))
    }
  }

  /** Runs [analysisSteps] in their order: a refused analysis refuses the plan at the place of its subject. */
  private fun planOf(
    request: ModPlanRequest,
    deletedSubjects: List<SymbolPointer>,
    analysisSteps: List<Pair<SymbolPointer, ModPlanFinalStep<ModPlanAnalysisResult>>>,
  ): ModPlan {
    val foundAnalyses = analysisSteps.map { [subject, analysisStep] ->
      when (val analysis = analysisStep.run(request)) {
        is ModPlanAnalysisResult.Refused -> return refusalOf(analysis.reason, subject)
        is ModPlanAnalysisResult.Found -> analysis
      }
    }
    val elements = deletedSubjects.map { it.element() ?: return gone() }
    val edits = lastPositionFirst(foundAnalyses.flatMap { it.edits }) + elements.map { ModPlanEdit.delete(it) }
    val conflicts = foundAnalyses.fold(ModPlanConflicts.EMPTY) { mergedConflicts, analysis ->
      mergedConflicts.merge(withoutBrokenAtTheSamePlace(analysis.conflicts, mergedConflicts))
    }.merge(ModPlanConflicts.builder().unloadedModules(elements).build())
    // As in the legacy safe delete, the command names everything that is deleted.
    val commandName =
      RefactoringBundle.message("safe.delete.command", RefactoringUIUtil.calculatePsiElementDescriptionList(elements.toTypedArray()))
    // As in the legacy safe delete, the deleted files go to the trash, as the user chose in the settings.
    return ModPlan.createPlan(edits, conflicts, eventElementsOf(elements, edits), commandName,
                              REFACTORING_ID, elements.first(), trashDeletedFiles = true)
  }

  /**
   * [elements] and every file or directory that [edits] delete; the legacy safe delete reports only its elements. A file in a deleted
   * directory is not in them, and neither is an element that a usage deletes.
   */
  private fun eventElementsOf(elements: List<PsiElement>, edits: List<ModPlanEdit>): List<PsiElement> {
    val deletedFiles = edits.filterIsInstance<ModPlanEdit.Delete>().map { it.file() }
    val psiManager = elements.first().manager
    val eventElements = LinkedHashSet(elements)
    deletedFiles.filter { file -> deletedFiles.none { VfsUtilCore.isAncestor(it, file, true) } }
      .mapNotNullTo(eventElements) { psiManager.findFile(it) ?: psiManager.findDirectory(it) }
    return eventElements.toList()
  }

  private fun invalidSubjectRefusal(subjects: List<SymbolPointer>): ModPlan.RefusedPlan? {
    for (subject in subjects) {
      if (!subject.isValid) {
        return gone()
      }
      val element = subject.element()
      if (element != null && !SafeDeleteProcessor.validElement(element)) {
        return refusalOf(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, subject)
      }
    }
    return null
  }

  /** @param subject the subject that causes the refusal; its element is read now, in the read action that ends the chain */
  private fun refused(
    chainBuilder: ModPlanChainBuilder,
    reason: ModPlanAnalysisResult.RefusedReason,
    subject: SymbolPointer,
  ): ModPlanChain<ModPlanFinalStep<ModPlan>> {
    val refusal = refusalOf(reason, subject)
    return chainBuilder.passValue(ModPlanFinalStep { refusal })
  }

  private fun refusalOf(reason: ModPlanAnalysisResult.RefusedReason, subject: SymbolPointer): ModPlan.RefusedPlan =
    ModPlan.RefusedPlan(reason, subject.element())

  companion object {
    internal const val REFACTORING_ID = "refactoring.safeDelete"

    @JvmStatic
    fun getInstance(): ModPlanSafeDeleteRefactoring = service()
  }
}

/**
 * The edits from the last position to the first, so that an edit does not move the position of the next, as the legacy safe delete
 * sorts its usages. An edit that has no position, such as a created, a moved or a deleted file, keeps its order after the others.
 */
private fun lastPositionFirst(edits: List<ModPlanEdit>): List<ModPlanEdit> {
  val positionedEdits = ArrayList<Pair<ModPlanEdit, PsiElement>>()
  val unpositionedEdits = ArrayList<ModPlanEdit>()
  for (edit in edits) {
    val element = when (edit) {
      is ModPlanEdit.Update -> edit.pointers().firstOrNull()?.element
      is ModPlanEdit.Delete, is ModPlanEdit.Create, is ModPlanEdit.Move -> null
    }
    if (element != null) {
      positionedEdits.add(edit to element)
    }
    else {
      unpositionedEdits.add(edit)
    }
  }
  return positionedEdits.sortedWith { a, b -> PsiUtilCore.compareElementsByPosition(b.second, a.second) }.map { it.first } +
         unpositionedEdits
}

/**
 * The broken conflicts of [conflicts] whose place has no broken conflict in [earlierConflicts], and its other conflicts. A usage that
 * blocks two deleted elements, such as a call of the accessor of a deleted record component, is found by both; the legacy safe delete
 * keeps the first of equal usages, so the place keeps the conflict of the element that is deleted first.
 */
private fun withoutBrokenAtTheSamePlace(conflicts: ModPlanConflicts, earlierConflicts: ModPlanConflicts): ModPlanConflicts {
  val brokenPlaces = earlierConflicts.conflicts.filter { it.kind == ModPlanConflictKind.BROKEN }
    .mapNotNullTo(HashSet()) { it.location }
  if (brokenPlaces.isEmpty()) return conflicts
  return ModPlanConflicts(conflicts.conflicts.filter { it.kind != ModPlanConflictKind.BROKEN || it.location !in brokenPlaces })
}

private fun gone() = ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, null as PsiElement?)
