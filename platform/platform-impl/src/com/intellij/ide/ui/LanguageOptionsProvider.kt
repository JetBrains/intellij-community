// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.search.OptionDescription

/**
 * @author Alexander Lobas
 */
internal class LanguageOptionsProvider : OptionsSearchTopHitProvider.ApplicationLevelProvider {
  override fun getId() = "language"

  override fun getOptions() = listOf(OptionDescription(IdeBundle.message("combobox.language.option"), "preferences.language.and.region",
                                                       null, null, IdeBundle.message("title.language.and.region")))
}