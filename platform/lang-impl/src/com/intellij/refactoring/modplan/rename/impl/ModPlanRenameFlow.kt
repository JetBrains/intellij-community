// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.RefactoringSettings
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.impl.ModPlanFlow
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring
import com.intellij.refactoring.modplan.rename.impl.ModPlanRenameFlow.REGISTRY_KEY
import com.intellij.refactoring.rename.RenameViewDescriptor
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * The rename of the rename dialog and of the inline rename of members on the ModPlan engine: the request of [ModPlanFlow] after the questions
 * that the legacy UI asked, what is renamed and the options, which the request answers; the related renames, the conflicts and the
 * occurrences in comments are asked by the flow. The preview shows every renamed element with the header of the legacy rename, such as
 * "Function to be renamed to bar". It is on only if the registry key [REGISTRY_KEY] is, and only for elements that the engine
 * supports; the legacy rename runs for everything else.
 */
@ApiStatus.Internal
object ModPlanRenameFlow {
  private const val REGISTRY_KEY = "refactoring.rename.modPlan"

  /**
   * Runs the rename of [element] to [newName] if the flow handles it. The user is asked in an interactive run only, not in a unit test.
   *
   * @param preview whether the user pressed Preview in the legacy UI, see [ModPlanRefactoring.PREVIEW]
   * @param factories the automatic renamers whose option the user turned on; the ones with no option run always, as in the legacy rename
   * @param additionalRenames the elements of `RenameProcessor.addElement`, by their new names
   * @return [ModPlanFlow.Result.LEGACY] if the legacy rename has to run. Otherwise what happened: the legacy dialog closes after
   * [ModPlanFlow.Result.DONE], [ModPlanFlow.Result.FAILED] or [ModPlanFlow.Result.PREVIEW_SHOWN], and stays open after
   * [ModPlanFlow.Result.CANCELLED]
   */
  @JvmStatic
  @RequiresEdt
  fun run(
    project: Project,
    element: PsiElement,
    newName: String,
    scope: SearchScope,
    searchInComments: Boolean,
    searchTextOccurrences: Boolean,
    preview: Boolean,
    factories: List<AutomaticRenamerFactory>,
    additionalRenames: Map<PsiElement, String>,
  ): ModPlanFlow.Result {
    if (!Registry.`is`(REGISTRY_KEY)) return ModPlanFlow.Result.LEGACY
    // A factory with no id has no switch, so the request cannot turn it on.
    if (factories.any { ModPlanRenameOptions.optionForRenamer(it) == null }) return ModPlanFlow.Result.LEGACY
    // The legacy UI answered every option, the switches that are off too, so the flow does not ask them again.
    var optionValues = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, searchInComments)
      .with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, searchTextOccurrences)
      .with(ModPlanRenameOptions.RENAME_RELATED, RefactoringSettings.getInstance().RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG)
      .with(ModPlanRefactoring.PREVIEW, preview)
    for (factory in AutomaticRenamerFactory.EP_NAME.extensionList) {
      val renamerOption = ModPlanRenameOptions.optionForRenamer(factory) ?: continue
      optionValues = optionValues.with(renamerOption, factory in factories || optionValues.get(renamerOption))
    }
    val request = runReadActionBlocking {
      ModPlanRenameRefactoring.request(project, element, newName, substitute = false, optionValues, scope, additionalRenames)
    }
    val interactive = !ApplicationManager.getApplication().isUnitTestMode
    return RenameFlow().run(request, interactive) {}
  }

  /** The preview shows every element that the plan renames, with the header of the legacy rename, as `RenameProcessor` does. */
  private class RenameFlow : ModPlanFlow(ModPlanRenameRefactoring.getInstance()) {
    override fun previewTargets(plan: ModPlan.AcceptedPlan, request: ModPlanRequest): PreviewTargets {
      val renames = plan.writeEvents.get().beforeData?.getUserData(RefactoringEventData.RENAMES_KEY)
      if (renames.isNullOrEmpty()) return super.previewTargets(plan, request)
      val descriptor = RenameViewDescriptor(LinkedHashMap(renames))
      return PreviewTargets(descriptor.elements.toList(), descriptor.processedElementsHeader)
    }
  }
}
