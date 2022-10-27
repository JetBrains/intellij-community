// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.choice

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithChoice
import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.openapi.util.Iconable
import com.intellij.ui.IconManager
import com.intellij.util.PlatformIcons
import javax.swing.Icon

/**
 * Intention action that is used as a variant of [IntentionActionWithChoice].
 *
 * The action should implement [invoke], requests to [applyFix] would be proxied to [invoke].
 *
 * The action requires [index] param, so it can maintain order of variants in
 * quick-fix popup.
 */
abstract class ChoiceVariantIntentionAction : IntentionAndQuickFixAction(), HighlightInfoType.Iconable, Iconable, CustomizableIntentionAction,
                                              Comparable<IntentionAction> {
  companion object {
    private val EMPTY_ICON by lazy {
      IconManager.getInstance().createEmptyIcon(PlatformIcons.PUBLIC_ICON)
    }
  }

  abstract val index: Int

  override fun isShowSubmenu(): Boolean = false

  override fun getIcon(): Icon = EMPTY_ICON

  override fun getIcon(flags: Int): Icon = EMPTY_ICON

  override fun compareTo(other: IntentionAction): Int {
    if (familyName != other.familyName) return this.familyName.compareTo(other.familyName)

    if (other is ChoiceTitleIntentionAction) {
      return 1
    }

    if (other is ChoiceVariantIntentionAction) {
      return this.index - other.index
    }

    return 0
  }
}