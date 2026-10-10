// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete.impl

import com.intellij.refactoring.RefactoringSettings
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import org.jetbrains.annotations.ApiStatus

/**
 * Keeps the search options of the safe delete in the common settings of the refactoring, as the legacy dialog does for an element that
 * has no delegate of its own. It serves the elements that no language provider owns.
 */
@ApiStatus.Internal
object ModPlanSettingsSearchStorage : ModPlanQuestion.Storage {
  override fun load(optionValues: ModPlanOptionValues): ModPlanOptionValues {
    val settings = RefactoringSettings.getInstance()
    return optionValues
      .with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, settings.SAFE_DELETE_SEARCH_IN_COMMENTS)
      .with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, settings.SAFE_DELETE_SEARCH_IN_NON_JAVA)
  }

  override fun store(answers: ModPlanOptionValues) {
    val settings = RefactoringSettings.getInstance()
    settings.SAFE_DELETE_SEARCH_IN_COMMENTS = answers.get(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS)
    settings.SAFE_DELETE_SEARCH_IN_NON_JAVA = answers.get(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE)
  }
}
