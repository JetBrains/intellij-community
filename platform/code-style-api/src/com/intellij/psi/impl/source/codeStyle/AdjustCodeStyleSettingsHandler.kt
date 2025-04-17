// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Allows customizing handling `Adjust code style settings` action.
 */
@ApiStatus.Internal
interface AdjustCodeStyleSettingsHandler {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<AdjustCodeStyleSettingsHandler> =
      create<AdjustCodeStyleSettingsHandler>("com.intellij.adjustCodeStyleSettingsHandler")
  }

  /**
   * Handles the action to adjust code style settings for a given PSI element and project.
   */
  fun handleAdjustCodestyleAction(psiElement: PsiElement, project: Project)

  /**
   * Determines whether a formatter is suitable for the specific file.
   */
  fun isApplicableFor(psiElement: PsiElement, project: Project): Boolean
}