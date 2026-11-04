// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename

import com.intellij.lang.findUsages.DescriptiveNameUtil
import com.intellij.model.SymbolPointer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanConflicts
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.refactoring.modplan.ModPlanFinalStep
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.ElementsForRename
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.SubjectUsage.CollisionKind
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.Substitution
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.function.Supplier

private typealias RenameChain = ModPlanChain<ModPlanFinalStep<ModPlan>>

/**
 * The rename on the ModPlan engine: the new name is an option of the request, see [request]. The [chain] of one request runs in the
 * order of the legacy rename, with a dialog or inline:
 * - for the requested subject: [ModPlanRenameProvider.substituteRequest] of its owner, which can replace it, such as by the base method
 *   of an override. Then the engine asks the options of the rename for the target, kept in the
 *   [storage][ModPlanRenameProvider.searchStorage] of the owner of the target. Then [ModPlanRenameProvider.prepare] of every provider adds
 *   what it renames with the target. If the target already has the new name, the chain ends with an accepted plan with no write. Then no
 *   refusal, no preparation, no related rename and no search runs;
 * - the related renames: if a renamer factory is on and applicable to a subject, the search of the subject runs first. The
 *   [renamers][ModPlanRenameProvider.renamers] of the subject get its usages. A question for each renamer that has something
 *   to rename. Then the search of every element that the chosen ones add. An element with an unresolvable collision keeps its name. Then
 *   [ModPlanRenameProvider.prepare] of every provider for every element that stays;
 * - the search: [ModPlanRenameProvider.findUsages] of the owner for every subject that has no usages yet. It runs after the last
 *   question, as a plain search with no question. If no renamer adds an element, each subject is searched once;
 * - the final step: [ModPlanRenameProvider.analyze] of every subject, then the plan, whose usages are the places of the usages. The
 *   analysis of the target also gives the conflicts of the new names.
 *
 * A question that is answered in the request is not asked, so a caller that asked with its own UI, such as the legacy rename dialog, puts
 * its answers in the options, and a headless caller is asked nothing. As in the legacy rename, what is renamed is computed once in a run; a
 * run that starts again computes it again. The request does not change: only what is renamed so far, [ElementsForRename], goes from one
 * step to the next.
 */
@Service(Service.Level.APP)
class ModPlanRenameRefactoring : ModPlanRefactoring {
  override fun id(): String = REFACTORING_ID

  override fun title(): String = RefactoringBundle.message("rename.title")

  override fun targetsNodeText(): String = UsageViewBundle.message("node.targets")

  override fun nonCodeUsagesHeader(nonCodeUsages: Int, files: Int): String {
    return RefactoringBundle.message("comments.elements.header", UsageViewBundle.getOccurencesString(nonCodeUsages, files))
  }

  /** The subject is owned by a registered [ModPlanRenameProvider]; it searches nothing. */
  override fun isSupported(request: ModPlanRequest): Boolean {
    return super.isSupported(request) && request.subjects().all { ownerOf(it) != null }
  }
  override fun chain(chainBuilder: ModPlanChainBuilder): RenameChain {
    val subject = chainBuilder.request.subjects().first()
    val newName = chainBuilder.request.optionValues().get(ModPlanRenameOptions.NEW_NAME)
    if (subject.element() == null) {
      return chainEndingWithRefusedPlan(chainBuilder, ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, subject)
    }
    val owner = ownerOf(subject)
                ?: return chainEndingWithRefusedPlan(chainBuilder, ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, subject)
    return owner.substituteRequest(chainBuilder, subject, newName).andThen { afterRequested, substitution ->
      if (substitution == null) {
        return@andThen chainEndingWithRefusedPlan(afterRequested, ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, subject)
      }
      val target = substitution.target
      val targetElement = target.element()
                          ?: return@andThen chainEndingWithRefusedPlan(afterRequested, ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, subject)
      // The owner may rename an element of another provider instead, such as a base method in another language.
      val targetOwner = ownerOf(target)
                        ?: return@andThen chainEndingWithRefusedPlan(afterRequested, ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, subject)
      val questions = ModPlanRenameUtil.renameQuestions(targetElement, targetOwner.searchStorage(target))
      afterRequested.askAll(questions) { afterQuestions -> renameTarget(afterQuestions, subject, substitution) }
    }
  }

  private fun renameTarget(afterQuestions: ModPlanChainBuilder, subject: SymbolPointer, substitution: Substitution): RenameChain {
    val target = substitution.target
    if (target.element() == null) {
      return chainEndingWithRefusedPlan(afterQuestions, ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, subject)
    }
    val optionValues = afterQuestions.request.optionValues()
    val additionalRenames = afterQuestions.request.subjects().drop(1).foldIndexed(ElementsForRename.EMPTY) { index, renames, additional ->
      renames.plus(ElementsForRename.of(additional, optionValues.get(ModPlanRenameOptions.newNameOfSubject(index + 1))))
    }
    if (additionalRenames.subjects().isEmpty() && (target.element() as? PsiNamedElement)?.name == substitution.newName) {
      return unchangedPlan(afterQuestions, target, substitution)
    }
    refusalOfTargetBeforeAnalysis(target, substitution.newName)?.let { refusal ->
      return afterQuestions.passValue(ModPlanFinalStep { refusal })
    }
    val elements = ElementsForRename.of(target, substitution.newName).plus(additionalRenames)
    val searches = ModPlanRenameSearches()
    return prepareSubject(afterQuestions, target, elements)
      .andThen { afterPrepare, prepared -> addRelatedRenames(afterPrepare, prepared, searches) }
      .andThen { afterRenamers, allElements -> searchAndCreatePlan(afterRenamers, allElements, searches) }
  }

  private fun unchangedPlan(
    afterRequested: ModPlanChainBuilder,
    target: SymbolPointer,
    substitution: Substitution,
  ): ModPlanChain<ModPlanFinalStep<ModPlan>> {
    return afterRequested.finish { _ ->
      val element = target.element()
                    ?: return@finish ELEMENT_GONE_PLAN
      val commandName = RefactoringBundle.message("renaming.0.1.to.2", UsageViewUtil.getType(element),
                                                  DescriptiveNameUtil.getDescriptiveName(element), substitution.newName)
      ModPlan.createPlan(emptyList(), ModPlanConflicts.EMPTY, commandName, REFACTORING_ID, element, false, emptyList(),
                         writeEventsForListeners(mapOf(element to substitution.newName), emptyList()))
    }
  }

  /** @return the chain that ends with the plan that refuses the run at the place of [subject] */
  private fun chainEndingWithRefusedPlan(
    chainBuilder: ModPlanChainBuilder,
    reason: ModPlanAnalysisResult.RefusedReason,
    subject: SymbolPointer,
  ): RenameChain {
    val refusal = ModPlan.RefusedPlan(reason, subject.element(), null)
    return chainBuilder.passValue(ModPlanFinalStep { refusal })
  }

  /**
   * Ends with the final step that searches every subject of [elements] and creates the plan from the usages. The searches run in the
   * final step, after every question. A subject that is gone, read-only or has no owner refuses the run. The first subject is the
   * target. A subject of [searches] is not searched again: it gets the usages of its search.
   */
  private fun searchAndCreatePlan(chainBuilder: ModPlanChainBuilder, elements: ElementsForRename, searches: ModPlanRenameSearches): RenameChain {
    val subjects = elements.subjects()
    if (subjects.isEmpty()) {
      return chainBuilder.passValue(ModPlanFinalStep { ELEMENT_GONE_PLAN })
    }
    val owners = ArrayList<Pair<SymbolPointer, ModPlanRenameProvider>>()
    for (subject in subjects) {
      val element = subject.element()
      // An element that the rename adds can be one of a library, such as the base method of a method of the project.
      if (element == null || ModPlanRefactoring.isInReadOnlyContainer(element)) {
        return chainEndingWithRefusedPlan(chainBuilder, ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, subject)
      }
      val owner = ownerOf(subject)
                  ?: return chainEndingWithRefusedPlan(chainBuilder, ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, subject)
      owners.add(subject to owner)
    }
    return chainBuilder.finish { request -> createPlanFromUsages(request, elements, owners, searches) }
  }

  /**
   * The plan of the usages of the subjects of [owners], each with its owner; the first subject is the target. The places of the usages
   * are the usages of the plan, which the user may exclude in a preview. A place that two subjects found goes to the first one. A subject
   * whose edits are refused refuses the plan. The analysis of the target gives the conflicts of the new names, as in the legacy rename.
   *
   * [searches] gives the usages, so each subject is searched once in a run. The final step runs again for an exclusion, see
   * [ModPlanRefactoring.EXCLUDED_USAGES]. Then each owner gets the usages that are left, as the legacy rename gives `renameElement` the
   * usages that are left. With [ModPlanRenameOptions.SKIP_READ_ONLY_USAGES] a usage in a read-only file is left out as an excluded one.
   */
  private fun createPlanFromUsages(
    request: ModPlanRequest,
    elementsForRename: ElementsForRename,
    owners: List<Pair<SymbolPointer, ModPlanRenameProvider>>,
    searches: ModPlanRenameSearches,
  ): ModPlan {
    // A SmartPsiFileRange hashes only its file, and its equality takes a read action, so the sets hold the file and the offsets.
    fun keyOf(place: SmartPsiFileRange): Triple<VirtualFile?, Int, Int>? =
      place.range?.let { range -> Triple(place.virtualFile, range.startOffset, range.endOffset) }

    // A place that an earlier subject found is its own; the usages of one subject may share a place, such as a collision and a reference.
    val placesOfEarlierSubjects = HashSet<Triple<VirtualFile?, Int, Int>>()
    val skipReadOnlyUsages = request.optionValues().get(ModPlanRenameOptions.SKIP_READ_ONLY_USAGES)
    val keptUsages = owners.map { [subject, owner] ->
      val usagesOfSubject = searches.usagesOf(subject, owner, elementsForRename, request).filter { usage ->
        usage.collisionKind() == CollisionKind.RESOLVABLE || usage.place()?.let { keyOf(it) } !in placesOfEarlierSubjects
      }
      usagesOfSubject.filterNot { it.collisionKind() == CollisionKind.RESOLVABLE }
        .mapNotNullTo(placesOfEarlierSubjects) { usage -> usage.place()?.let { keyOf(it) } }
      usagesOfSubject.filter { usage ->
        val place = usage.place()
        val file = place?.virtualFile
        when {
          place == null -> true
          ModPlanRefactoring.isExcluded(request, place) -> false
          // The same check as in ModPlanExecutor.run.
          skipReadOnlyUsages && file != null &&
          (!file.isWritable || FileDocumentManager.getInstance().getCachedDocument(file)?.isWritable == false) -> false
          else -> true
        }
      }
    }
    val renamed = elementsForRename.elements()
                  ?: return ELEMENT_GONE_PLAN
    val edits = ArrayList<ModPlanEdit>()
    var usageConflicts = ModPlanConflicts.EMPTY
    owners.forEachIndexed { index, [subject, owner] ->
      when (val result = owner.analyze(subject, elementsForRename, keptUsages[index])) {
        is ModPlanAnalysisResult.Refused -> return ModPlan.RefusedPlan(result.reason, subject.element(), null)
        is ModPlanAnalysisResult.Found -> {
          edits.addAll(result.edits)
          usageConflicts = usageConflicts.merge(result.conflicts)
        }
      }
    }
    // A provider can edit a file of a language that the ModPlan rename does not support, such as an import of a Go file.
    if (ModPlanRenameLanguages.firstUnsupportedFile(edits) != null) {
      return ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, renamed.keys.first(), null)
    }
    val elements = renamed.keys.toList()
    val conflicts = usageConflicts.merge(ModPlanConflicts.builder().unloadedModules(elements).build())
    val targetElement = elements.first()
    if (!conflicts.needsConfirmation()) {
      refusalOfNamesBeforeWrite(renamed)?.let { return it }
    }
    return try {
      val commandName = RefactoringBundle.message("renaming.0.1.to.2", UsageViewUtil.getType(targetElement),
                                                  DescriptiveNameUtil.getDescriptiveName(targetElement), renamed.getValue(targetElement))
      val usagePlaces = keptUsages.flatten().mapNotNull { it.place() }.distinctBy { keyOf(it) ?: it }
      ModPlan.createPlan(edits, conflicts, commandName, REFACTORING_ID, targetElement, false, usagePlaces,
                         writeEventsForListeners(renamed, usagePlaces))
    }
    // The language refuses an edit, as it refuses a write of the legacy rename.
    catch (e: IncorrectOperationException) {
      ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.EDIT_FAILED, targetElement, e.message)
    }
  }

  /**
   * What the write tells the listeners, as the legacy rename does: the data before the write has the primary element, every rename and
   * the usages that the plan writes. The data after it has the primary element, and every renamed element is reported to its listeners.
   * A usage is reported as its place: the file and the range. A usage with no place is not reported.
   */
  private fun writeEventsForListeners(
    renamed: Map<PsiElement, String>,
    usagePlaces: List<SmartPsiFileRange>,
  ): Supplier<ModPlanRefactoring.WriteEvent> {
    val pointers = renamed.mapKeys { [element, _] -> SmartPointerManager.createPointer(element) }
    val primaryPointer = pointers.keys.first()
    return Supplier {
      val beforeData = RefactoringEventData()
      primaryPointer.element?.let { beforeData.addElement(it) }
      beforeData.addRenames(pointers.entries.mapNotNull { [pointer, newName] -> pointer.element?.let { it to newName } }.toMap())
      beforeData.addUsages(usagePlaces.filter { it.range != null }.map { usagePlace -> UsageInfo(usagePlace, usagePlace, false, false) })
      val changes = pointers.keys.mapNotNull { pointer ->
        pointer.element?.let { ModPlanRefactoring.ElementChange(it, ModPlanRefactoring.ElementChange.Kind.RENAMED) { pointer.element } }
      }
      ModPlanRefactoring.WriteEvent(beforeData, changes) {
        RefactoringEventData().also { afterData -> primaryPointer.element?.let { afterData.addElement(it) } }
      }
    }
  }

  companion object {
    private const val REFACTORING_ID = "refactoring.rename"
    internal val ELEMENT_GONE_PLAN: ModPlan.RefusedPlan =
      ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, null as PsiElement?, null)

    @JvmStatic
    fun getInstance(): ModPlanRenameRefactoring = service()

    /**
     * Builds the request of the rename of [target] to [newName].
     *
     * @param substitute whether the provider may rename another element instead of [target], such as the base method of an override, see
     * [ModPlanRenameOptions.SUBSTITUTE_TARGET]
     * @param searchScope where the rename searches the usages; null for the project
     * @param additionalRenames the elements that the caller renames with [target], by their new names, as `RenameProcessor.addElement`.
     * They are the subjects of the request after the first one. No provider substitutes or prepares them.
     */
    @JvmStatic
    @JvmOverloads
    @RequiresReadLock
    fun request(
      project: Project,
      target: PsiElement,
      newName: String,
      substitute: Boolean,
      optionValues: ModPlanOptionValues,
      searchScope: SearchScope? = null,
      additionalRenames: Map<PsiElement, String> = emptyMap(),
    ): ModPlanRequest {
      var values = optionValues.with(ModPlanRenameOptions.NEW_NAME, newName)
        .with(ModPlanRenameOptions.SUBSTITUTE_TARGET, substitute)
      val subjects = arrayListOf(SymbolPointer.of(target))
      for ([additionalElement, additionalName] in additionalRenames) {
        values = values.with(ModPlanRenameOptions.newNameOfSubject(subjects.size), additionalName)
        subjects.add(SymbolPointer.of(additionalElement))
      }
      if (searchScope == null) return ModPlanRequest(project, subjects, values)
      return ModPlanRequest(project, subjects, values, ModPlanRequest.searchScopeSupplier(searchScope))
    }

    /**
     * @return the provider that owns [subject]: the first one in the extension order that is applicable to it; null if it is legacy only
     * for [subject], or if the ModPlan rename does not support its language, see [ModPlanRenameLanguages]
     */
    internal fun ownerOf(subject: SymbolPointer): ModPlanRenameProvider? {
      val element = subject.element()
      if (element != null && !ModPlanRenameLanguages.isSupported(element)) return null
      return ModPlanRenameProvider.EP_NAME.findFirstSafe { it.isOwner(subject) }?.takeUnless { it.isLegacyOnly(subject) }
    }
  }
}
