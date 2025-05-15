// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.choice

import com.intellij.codeInsight.intention.AbstractEmptyIntentionAction
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithChoice
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Intention action that is used as a title of [IntentionActionWithChoice].
 *
 * Note, that this action should be non-selectable in any UI, since it does
 * not have any implementation for invoke.
 */
open class ChoiceTitleIntentionAction(@IntentionFamilyName private val family: String, @IntentionName private val title: String)
  : AbstractEmptyIntentionAction(), CustomizableIntentionAction, LocalQuickFix, Comparable<IntentionAction>  {
  override fun isShowIcon(): Boolean = false

  override fun isSelectable(): Boolean = false

  override fun isShowSubmenu(): Boolean = false

  override fun getFamilyName(): String = family

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean = true

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {}

  override fun getText(): String = title

  override fun compareTo(other: IntentionAction): Int {
    if (familyName != other.familyName) return familyName.compareTo(other.familyName)

    if (other is ChoiceVariantIntentionAction) {
      return -1
    }

    return 0
  }
}
