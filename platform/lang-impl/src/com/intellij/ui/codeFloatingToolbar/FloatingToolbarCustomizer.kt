// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point to configure and enable floating code toolbar for the specific language.
 * @see CodeFloatingToolbar
 * @see [FloatingToolbarCustomizer.DefaultGroup]
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface FloatingToolbarCustomizer {
  companion object {
    private val EP = LanguageExtension<FloatingToolbarCustomizer>("com.intellij.lang.floatingToolbarCustomizer")

    fun findActionGroupFor(language: Language): String? {
      return EP.allForLanguage(language).firstNotNullOfOrNull { it.getActionGroup() }
    }
  }

  /**
   * @return id of the action group to be shown in the toolbar, or null if the toolbar shouldn't be available
   */
  fun getActionGroup(): String?

  class DefaultGroup: FloatingToolbarCustomizer {
    override fun getActionGroup(): String {
      return "Floating.CodeToolbar"
    }
  }
}