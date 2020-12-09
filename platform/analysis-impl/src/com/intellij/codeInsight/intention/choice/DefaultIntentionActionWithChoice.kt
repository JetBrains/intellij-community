// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.choice

import com.intellij.codeInsight.intention.IntentionActionWithChoice
import com.intellij.codeInspection.LocalQuickFix

/**
 * Default intention action with choice that uses [ChoiceTitleIntentionAction]
 * and [ChoiceVariantIntentionAction] as title and variant action respectively.
 *
 * In most cases this class should be used to create new choice-based intention actions.
 */
interface DefaultIntentionActionWithChoice : IntentionActionWithChoice<ChoiceTitleIntentionAction, ChoiceVariantIntentionAction> {
  /**
   * Use this function to add intention-action with choice to
   * problem descriptor
   */
  @JvmDefault
  fun getAllAsFixes(): List<LocalQuickFix> {
    val result = ArrayList<LocalQuickFix>()
    result.add(title)
    result.addAll(variants)
    require(result.map { it.familyName }.toSet().size == 1) {
      "All default intention actions with choice are expected to have same family"
    }
    return result
  }
}