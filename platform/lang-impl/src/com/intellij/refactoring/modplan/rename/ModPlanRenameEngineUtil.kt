// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename

import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanOption
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.ElementsForRename
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.SubjectUsage
import com.intellij.refactoring.modplan.rename.ModPlanRenameProvider.SubjectUsage.CollisionKind
import com.intellij.refactoring.rename.AutomaticRenamingDialog
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameInputValidatorRegistry
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.Nls
import java.util.IdentityHashMap

/* Helpers of the engine [ModPlanRenameRefactoring], not for providers. */

/**
 * The searches of one run of the rename. Each subject is searched once: before the questions of the related renames or in the final
 * step. The final step that runs again for an exclusion gets the same usages. The usages hold PSI, so a subject is searched again if PSI
 * changed since its search.
 */
internal class ModPlanRenameSearches {
  private class Search(val usages: List<SubjectUsage>, val modificationCount: Long)

  private val searches = IdentityHashMap<SymbolPointer, Search>()

  /**
   * @param elements everything that the run renames now
   * @return what [owner] finds for [subject], see [ModPlanRenameProvider.findUsages]: the usages of the earlier search of [subject] if
   * PSI did not change since it
   */
  @RequiresReadLock
  @Synchronized
  fun usagesOf(subject: SymbolPointer, owner: ModPlanRenameProvider, elements: ElementsForRename, request: ModPlanRequest): List<SubjectUsage> {
    val modificationCount = PsiModificationTracker.getInstance(request.project()).modificationCount
    searches[subject]?.takeIf { it.modificationCount == modificationCount }?.let { return it.usages }
    val usages = owner.findUsages(subject, elements, request)
    searches[subject] = Search(usages, modificationCount)
    return usages
  }

  /** Forgets the search of every subject that is not in [subjects], so a later step searches it again. */
  @Synchronized
  fun keepOnly(subjects: Collection<SymbolPointer>) {
    searches.keys.retainAll { searched -> subjects.any { it === searched } }
  }
}

/**
 * The refusal of the target before its analysis: one of a library; the general reasons of the legacy rename, such as an element out of
 * the project; a name that the language does not accept.
 */
internal fun refusalOfTargetBeforeAnalysis(target: SymbolPointer, newName: String): ModPlan.RefusedPlan? {
  val element = target.element() ?: return ModPlanRenameRefactoring.ELEMENT_GONE_PLAN
  // A provider owns the element, so it has a processor.
  fun notApplicable(message: @NlsContexts.DialogMessage String? = null) = ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, element, message)
  PsiElementRenameHandler.getRenameErrorMessage(element.project, null, element, true)?.let { return notApplicable(it) }
  if (ModPlanRefactoring.isInReadOnlyContainer(element)) return notApplicable()
  RenameInputValidatorRegistry.getInputErrorValidator(element)?.`fun`(newName)?.let { return notApplicable(it) }
  if (!RenameUtil.isValidName(element.project, element, newName)) {
    val message = try {
      RenameUtil.checkRename(element, newName)
      null
    }
    catch (e: IncorrectOperationException) {
      e.message
    }
    return notApplicable(message)
  }
  return null
}

/**
 * The refusal of [renamed], the elements by their new names, before the write. As the legacy rename does after its conflicts, the
 * language refuses a name that it cannot write, such as a keyword.
 */
internal fun refusalOfNamesBeforeWrite(renamed: Map<PsiElement, String>): ModPlan.RefusedPlan? {
  for ([element, newName] in renamed) {
    try {
      RenameUtil.checkRename(element, newName)
    }
    catch (e: IncorrectOperationException) {
      return ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, element, e.message)
    }
  }
  return null
}

/**
 * Asks what the renamers of every subject of [elements] rename, then adds the chosen elements and prepares them, as the legacy rename
 * adds the elements of its renamers after their dialogs. It asks one question for each renamer that has something to rename.
 *
 * A renamer gets the usages of its subject, so the search of each subject that a factory is applicable to runs here, before the
 * questions. The renamers are made again after the questions, as a function after a question does not keep what it changes.
 *
 * @param searches the searches of the run: the final step does not search again a subject that is searched here
 * @return everything that the run renames
 */
internal fun addRelatedRenames(
  chainBuilder: ModPlanChainBuilder,
  elements: ElementsForRename,
  searches: ModPlanRenameSearches,
): ModPlanChain<ElementsForRename> {
  val request = chainBuilder.request
  val renamers = renamersOf(request, elements, searches)
  if (renamers.isEmpty()) return chainBuilder.passValue(elements)
  val questionsToAsk = renamers.filter { it.renamer.hasAnythingToRename() }.mapIndexed { index, renamerOfFactory ->
    val renamer = renamerOfFactory.renamer
    val factoryId = renamerOfFactory.factoryId
    val elementKeys = renamer.elements.filter { renamer.getNewName(it) != null }.mapNotNull { ModPlanRenameOptions.keyOf(factoryId, it) }
    val suggestedRenames = renamer.elements.mapNotNull { element -> renamer.getNewName(element)?.let { suggestedRename(factoryId, element, it) } }
    RelatedRenamesQuestion(factoryId, elementKeys, renamer.dialogDescription, suggestedRenames, renamer.isSelectedByDefault) { currentValues ->
      renamersOf(ModPlanChain.withOptionValues(request, currentValues), elements, searches)
        .filter { it.renamer.hasAnythingToRename() }.getOrNull(index)?.renamer
    }
  }
  return chainBuilder.askAll(questionsToAsk) { afterQuestions ->
    val answeredRequest = afterQuestions.request
    val chosenRenames = elementsThatRenamersRenameWithChoice(answeredRequest, elements, renamersOf(answeredRequest, elements, searches))
    searchAndPrepareChosenRenames(afterQuestions, elements, chosenRenames, searches)
  }
}

/**
 * Searches each element of [chosenRenames], then adds the ones that stay to [elements] and prepares them. An element with a
 * [CollisionKind.UNRESOLVABLE] usage keeps its name and is not prepared, as in `AutomaticRenamer.findUsages`. The rest is renamed.
 * Nothing shows the element that is left out. A chosen element with no owner stays with no search, as the legacy rename renames it,
 * and the final step refuses the run as not supported. Only a chosen element is prepared: in the legacy rename an element that
 * the rename of another one adds renames nothing more.
 *
 * If a chosen element stays with a search, [searches] keeps only the searches of the chosen elements. Then the final step searches the
 * other subjects again: a search reads the renamed elements, as the Java search of a static import of overloads.
 */
private fun searchAndPrepareChosenRenames(
  chainBuilder: ModPlanChainBuilder,
  elements: ElementsForRename,
  chosenRenames: ElementsForRename,
  searches: ModPlanRenameSearches,
): ModPlanChain<ElementsForRename> {
  val request = chainBuilder.request
  val elementsWithChosen = elements.plus(chosenRenames)
  val searchedSubjects = ArrayList<SymbolPointer>()
  var keptRenames = chosenRenames
  for (subject in chosenRenames.subjects()) {
    val owner = ModPlanRenameRefactoring.ownerOf(subject) ?: continue
    if (searches.usagesOf(subject, owner, elementsWithChosen, request).any { it.collisionKind() == CollisionKind.UNRESOLVABLE }) {
      keptRenames = keptRenames.without(subject)
    }
    else {
      searchedSubjects.add(subject)
    }
  }
  if (searchedSubjects.isNotEmpty()) searches.keepOnly(searchedSubjects)
  return keptRenames.subjects().fold(chainBuilder.passValue(elements.plus(keptRenames))) { chain, subject ->
    chain.andThen { afterPrevious, current -> prepareSubject(afterPrevious, subject, current) }
  }
}

/**
 * @return the owner of [subject], then every other provider, as `allForElement` gives the legacy processors. A provider that does not
 * add to the rename of [subject] passes, see [ModPlanRenameProvider.prepare]
 */
private fun providersOf(subject: SymbolPointer): List<ModPlanRenameProvider> {
  val owner = ModPlanRenameRefactoring.ownerOf(subject) ?: return emptyList()
  return listOf(owner) + ModPlanRenameProvider.EP_NAME.extensionList.filter { it !== owner }
}

/**
 * Adds what every provider of [subject] renames with it to [elements], as the legacy rename lets every processor of an element add to
 * the rename, see [ModPlanRenameProvider.prepare]. The new name of [subject] is the one in [elements].
 */
internal fun prepareSubject(chainBuilder: ModPlanChainBuilder, subject: SymbolPointer, elements: ElementsForRename): ModPlanChain<ElementsForRename> =
  providersOf(subject).fold(chainBuilder.passValue(elements)) { chain, provider ->
    chain.andThen { afterProvider, prepared ->
      if (prepared.newNameOf(subject) == null) return@andThen afterProvider.passValue(prepared)
      provider.prepare(afterProvider, subject, prepared)
    }
  }

/**
 * A renamer with the [AutomaticRenamerFactory.getOptionId] of the factory that made it: the lines of
 * [ModPlanRenameOptions.RELATED_RENAMES] name the renamer by it.
 */
private class RenamerOfFactory(val factoryId: String, val renamer: AutomaticRenamer)

/** @return the renamer factories that [ModPlanRenameOptions.RENAMERS] of [request] turns on */
private fun factoriesOf(request: ModPlanRequest): List<AutomaticRenamerFactory> {
  val renamerIds = ModPlanRenameOptions.renamerIds(request.optionValues())
  if (renamerIds.isEmpty()) return emptyList()
  val dumbService = DumbService.getInstance(request.project())
  return AutomaticRenamerFactory.EP_NAME.extensionList.filter { factory ->
    dumbService.isUsableInCurrentContext(factory) && factory.optionId in renamerIds
  }
}

/**
 * @return the renamers of every subject of [elementsForRename] that a factory of [request] is applicable to and that has an owner, see
 * [ModPlanRenameProvider.renamers]. The owner gets one factory at a time, so that each renamer keeps the id of its factory. A renamer gets
 * the usages of its subject from [searches]. As in the legacy rename, one with only synthetic elements to rename is not asked about, but
 * it renames them
 */
private fun renamersOf(
  request: ModPlanRequest,
  elementsForRename: ElementsForRename,
  searches: ModPlanRenameSearches,
): List<RenamerOfFactory> {
  val factories = factoriesOf(request)
  if (factories.isEmpty()) return emptyList()
  val subjectsOfRenamers = elementsForRename.subjects().filter { subject ->
    val element = subject.element()
    element != null && factories.any { it.isApplicable(element) }
  }.mapNotNull { subject -> ModPlanRenameRefactoring.ownerOf(subject)?.let { owner -> subject to owner } }
  // Every subject is searched before the first renamer is made.
  val usagesOfSubjects = subjectsOfRenamers.map { [subject, owner] -> searches.usagesOf(subject, owner, elementsForRename, request) }
  return subjectsOfRenamers.flatMapIndexed { index, [subject, owner] ->
    factories.flatMap { factory ->
      owner.renamers(subject, elementsForRename, listOf(factory), usagesOfSubjects[index]).map { RenamerOfFactory(factory.optionId!!, it) }
    }
  }
}

/**
 * @return the elements that [renamers] rename with the choice of [request], by their new names: every suggested one, or the chosen ones,
 * see [ModPlanRenameOptions.RELATED_RENAMES]; not the ones that [elementsForRename] renames already, and not a read-only one, such as
 * an element of a library. An element whose new name collides is in the result: [searchAndPrepareChosenRenames] leaves it out after
 * its search.
 */
private fun elementsThatRenamersRenameWithChoice(
  request: ModPlanRequest,
  elementsForRename: ElementsForRename,
  renamers: List<RenamerOfFactory>,
): ElementsForRename {
  if (renamers.isEmpty()) return ElementsForRename.EMPTY
  renamers.forEach { applyChoiceOfRelatedRenamesToRenamer(it, request.optionValues()) }
  val renamed = elementsForRename.elements() ?: emptyMap()
  var relatedRenames = ElementsForRename.EMPTY
  for (renamer in renamers.map { it.renamer }) {
    for (element in renamer.elements) {
      val newName = renamer.getNewName(element) ?: continue
      if (element !in renamed && !ModPlanRefactoring.isInReadOnlyContainer(element)) {
        relatedRenames = relatedRenames.plus(ElementsForRename.of(SymbolPointer.of(element), newName))
      }
    }
  }
  return relatedRenames
}

/**
 * Applies the choice of [options] to the renamer of [renamerOfFactory]: every suggested name if [ModPlanRenameOptions.RELATED_RENAMES] has
 * no value, else the names of its lines; an element that it has no new name for, or an empty one, keeps its name.
 */
private fun applyChoiceOfRelatedRenamesToRenamer(renamerOfFactory: RenamerOfFactory, options: ModPlanOptionValues) {
  if (!options.has(ModPlanRenameOptions.RELATED_RENAMES)) return
  val chosenRenames = ModPlanRenameOptions.relatedRenames(options.get(ModPlanRenameOptions.RELATED_RENAMES))
  val renamer = renamerOfFactory.renamer
  for (element in renamer.elements) {
    if (renamer.getNewName(element) == null) continue
    val newName = ModPlanRenameOptions.keyOf(renamerOfFactory.factoryId, element)?.let { chosenRenames[it] }
    if (newName.isNullOrEmpty()) {
      renamer.doNotRename(element)
    }
    else {
      renamer.setRename(element, newName)
    }
  }
}

/**
 * A rename that a renamer suggests, for the fallback form of [RelatedRenamesQuestion].
 *
 * @property key the key of the element in [ModPlanRenameOptions.RELATED_RENAMES]
 * @property location the location of the element, see [locationOf]
 */
private class SuggestedRename(val key: String, val oldName: String, val newName: String, val location: @Nls String?)

/** @return the rename of [element] to [newName] for the fallback form of [RelatedRenamesQuestion]; null if the element has no file */
@RequiresReadLock
private fun suggestedRename(factoryId: String, element: PsiElement, newName: String): SuggestedRename? {
  val key = ModPlanRenameOptions.keyOf(factoryId, element) ?: return null
  return SuggestedRename(key, (element as? PsiNamedElement)?.name.orEmpty(), newName, locationOf(element))
}

/** @return the name of the file of [element] and the 1-based number of its line, or only the file name if the file has no document */
@RequiresReadLock
private fun locationOf(element: PsiElement): @Nls String? {
  val file = element.containingFile ?: return null
  val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return file.name
  val lineNumber = (document.getLineNumber(element.textOffset) + 1).toString()
  return RefactoringBundle.message("modplan.rename.related.rename.file.0.line.1", file.name, lineNumber)
}

/**
 * The choice of what one renamer renames, with the dialog of the legacy rename. The answer adds a line to
 * [ModPlanRenameOptions.RELATED_RENAMES] for every element of the renamer: its new name, or an empty one for an element that keeps its
 * name. A cancelled dialog renames none of them, as in the legacy rename, which goes on.
 *
 * The question keeps no renamer, as a renamer holds PSI: [ask] makes it again with [findRenamer], in a modal progress. [findRenamer]
 * uses the usages of the searches of the chain, and searches again only if PSI changed since them.
 *
 * @property factoryId the [AutomaticRenamerFactory.getOptionId] of the factory of the renamer
 * @property elementKeys the keys of the elements that the renamer suggests a new name for
 * @property description the description of the dialog of the renamer, the label of the fallback form
 * @property suggestedRenames the suggested renames of the renamer, for the fallback form
 * @property selectedByDefault whether the fallback form starts with all [suggestedRenames] chosen, as [AutomaticRenamingDialog] does
 */
private class RelatedRenamesQuestion(
  private val factoryId: String,
  private val elementKeys: List<String>,
  private val description: @NlsContexts.Label String,
  private val suggestedRenames: List<SuggestedRename>,
  private val selectedByDefault: Boolean,
  private val findRenamer: (ModPlanOptionValues) -> AutomaticRenamer?,
) : ModPlanQuestion.Custom {
  override fun options(): List<ModPlanOption<*>> = listOf(ModPlanRenameOptions.RELATED_RENAMES)

  /** Answered if the answers decide about every element of the renamer, as another renamer answers the same option. */
  override fun isAnswered(currentValues: ModPlanOptionValues): Boolean {
    val answeredElementKeys = currentValues.answeredKeys(ModPlanRenameOptions.RELATED_RENAMES).map { it.substringBeforeLast('	') }
    return currentValues.has(ModPlanRenameOptions.RELATED_RENAMES) && answeredElementKeys.containsAll(elementKeys)
  }

  /**
   * A row for each suggested rename: a checkbox and an edit box of the new name. The chosen elements get the names of their edit boxes,
   * the others keep their names.
   */
  override fun fallback(): ModPlanQuestion.Form? {
    if (suggestedRenames.isEmpty()) return null
    val option = ModPlanRenameOptions.RELATED_RENAMES
    val rows = suggestedRenames.map { rename ->
      ModPlanQuestion.ValueRow(rename.key, RefactoringBundle.message("modplan.rename.related.rename.0.to", rename.oldName), rename.location)
    }
    val storage = object : ModPlanQuestion.Storage {
      override fun load(optionValues: ModPlanOptionValues): ModPlanOptionValues =
        suggestedRenames.fold(optionValues) { values, rename -> values.withValue(option, rename.key, selectedByDefault, rename.newName) }

      override fun store(answers: ModPlanOptionValues) {
      }
    }
    return ModPlanQuestion.Form.ofValueRows(option, description, rows, storage)
  }

  @RequiresEdt
  override fun ask(project: Project, currentValues: ModPlanOptionValues): ModPlanOptionValues {
    val renamer = runWithModalProgressBlocking(project, RefactoringBundle.message("rename.title")) {
      readAction { findRenamer(currentValues) }
    } ?: return currentValues
    val accepted = AutomaticRenamingDialog(project, renamer).showAndGet()
    val lines = runReadActionBlocking {
      renamer.elements.mapNotNull {
        ModPlanRenameOptions.relatedRename(factoryId, it, if (accepted) renamer.getNewName(it).orEmpty() else "")
      }
    }
    val allLines = (currentValues.get(ModPlanRenameOptions.RELATED_RENAMES).lines().filter { it.isNotEmpty() } + lines).distinct()
    return currentValues.with(ModPlanRenameOptions.RELATED_RENAMES, allLines.joinToString("\n"))
  }
}
