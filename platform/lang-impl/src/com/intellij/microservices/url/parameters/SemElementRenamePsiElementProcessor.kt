// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.parameters

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.microservices.url.references.QueryParameterSemElementSupport
import com.intellij.microservices.url.references.forbidExpensiveUrlContext
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.walkUp
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.intellij.util.SmartList
import com.intellij.util.containers.addIfNotNull

private class SemElementRenamePsiElementProcessor : RenamePsiElementProcessor() {

  override fun canProcessElement(element: PsiElement): Boolean = supportedElement(element)

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
    for (companion in getCompanions(element)) {
      allRenames[companion] = newName
    }
  }
}

private class RenameableSemElementFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
  override fun canFindUsages(element: PsiElement): Boolean = supportedElement(element)

  override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler =
    object : FindUsagesHandler(element) {

      override fun processElementUsages(element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions): Boolean {
        return runReadAction {
          getCompanions(element).all { companion ->
            super.processElementUsages(companion, processor, options) &&
            if (companion.containingFile != null) // all in-air elements are handled via references on the previous step
              processor.process(UsageInfo(companion))
            else true
          }
        } && super.processElementUsages(element, processor, options)
      }

      override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): Collection<PsiReference> {
        val result = SmartList<PsiReference>()
        for (companion in getCompanions(target, false)) {
          result.addAll(super.findReferencesToHighlight(companion, searchScope))
          result.addIfNotNull(createFakeReferenceForHighlighting(companion, target))
        }
        result.addAll(super.findReferencesToHighlight(target, searchScope))
        return result.distinctBy { ref -> ref.element to ref.rangeInElement }
      }

    }
}

private fun createFakeReferenceForHighlighting(psiElement: PsiElement, target: PsiElement): PsiReference? {
  val navigationElement = psiElement.navigationElement ?: return null
  val containingFile = navigationElement.containingFile ?: return null
  val identifierRange = HighlightUsagesHandler.getNameIdentifierRange(containingFile, navigationElement) ?: return null
  return PsiReferenceBase.Immediate(navigationElement, identifierRange.shiftLeft(navigationElement.textRange.startOffset), target)
}

private class SemElementRenamePsiReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    forbidExpensiveUrlContext {
      val target = queryParameters.elementToSearch
      val pathVariableDefinition = provide { createPomTargetFromSemElement(target) } as? PsiNamedElement
                                   ?: return@forbidExpensiveUrlContext null
      val name = pathVariableDefinition.name?.takeIf { it.isNotEmpty() } ?: return@forbidExpensiveUrlContext null
      val searchScope = target.containingFile?.let { LocalSearchScope(it) } ?: pathVariableDefinition.useScope

      val knowTargets = ConcurrentCollectionFactory.createConcurrentSet<PsiElement>().apply {
        add(pathVariableDefinition)
        add(target)
      }

      SearchService.getInstance()
        .searchWord(pathVariableDefinition.project, name)
        .inContexts(SearchContext.inCode())
        .inScope(searchScope)
        .buildQuery { (scope, start, offsetInStart) ->
          SmartList<PsiReference>().also { referencesAtOccurence ->
            for ((psiElement, offset) in walkUp(start, offsetInStart, scope)) {
              for (reference in PsiReferenceService.getService().getReferences(psiElement, PsiReferenceService.Hints(null, offset))) {
                val resolve = reference.resolve() ?: continue
                if (resolve in knowTargets) {
                  referencesAtOccurence.add(reference)
                  continue
                }
                if (provide { createPomTargetFromSemElement(resolve) } == pathVariableDefinition) {
                  referencesAtOccurence.add(reference)
                  if (knowTargets.add(resolve)) {
                    referencesAtOccurence.addIfNotNull(createFakeReferenceForHighlighting(resolve, target))
                  }
                }
              }
            }
          }
        }
    }?.forEach(consumer)

  }
}

private class RenameableSemElementUseScopeEnlarger : UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
    provide { createPomTargetFromSemElement(element) }?.let {
      return GlobalSearchScope.fileScope(element.containingFile)
    }
    return null
  }
}

private fun getCompanions(element: PsiElement, addPomTargetForSem: Boolean = true): List<PsiElement> {
  val companions = SmartList<PsiElement>()
  if (element is PomTargetPsiElement)
    for (support in renameableSemElementSupport) {
      val collection = support.findReferencingPsiElements(element.target)
      var touched = false
      for (psiElement in collection) {
        touched = true
        companions.add(psiElement)
      }
      if (touched)
        break
    }
  if (addPomTargetForSem) {
    provide { createPomTargetFromSemElement(element) }?.let { pomTargetPsiElement ->
      companions.add(pomTargetPsiElement)
      companions.addAll(getCompanions(pomTargetPsiElement, false).asSequence().filter { it != element })
    }
  }
  return companions
}

private fun supportedElement(element: PsiElement): Boolean {
  if (element is PomTargetPsiElement && renameableSemElementSupport.any { it.supportsTarget(element.target) })
    return true
  if (provide { createPomTargetFromSemElement(element) } != null) return true
  return false
}

private fun <T : Any> provide(call: RenameableSemElementSupport<*>.() -> T?): T? = renameableSemElementSupport.asSequence()
  .mapNotNull(call)
  .firstOrNull()

// could be an EP but is hardcoded for now
internal val renameableSemElementSupport: List<RenameableSemElementSupport<*>> =
  listOf(QueryParameterSemElementSupport, PathVariableSemElementSupport)

private fun <T : RenameableSemElement> RenameableSemElementSupport<T>.createPomTargetFromSemElement(semHolder: PsiElement): PomTargetPsiElement? {
  val queryParameterSem = getSemElement(semHolder) ?: return null
  val project = semHolder.project
  return createPomTargetPsi(project, queryParameterSem)
}
