// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
public interface FloatingToolbarCustomizer {
  companion object {
    private val EP = LanguageExtension<FloatingToolbarCustomizer>("com.intellij.lang.floatingToolbarCustomizer")

    fun findActionGroupFor(language: Language): String? {
      return EP.allForLanguage(language).firstNotNullOfOrNull { it.getActionGroup() }
    }
  }

  fun getActionGroup(): String?
}