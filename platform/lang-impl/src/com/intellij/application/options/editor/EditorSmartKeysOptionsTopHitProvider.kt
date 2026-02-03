// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.options.ConfigurableWithOptionDescriptors
import com.intellij.openapi.options.ex.ConfigurableWrapper

class EditorSmartKeysOptionsTopHitProvider : OptionsSearchTopHitProvider.ApplicationLevelProvider {
  override fun getId(): String = "editor.preferences.tabs"

  override fun getOptions(): Collection<OptionDescription> {
    return editorSmartKeysOptionDescriptors + EditorSmartKeysConfigurable().configurables
      .map { c -> if (c is ConfigurableWrapper) c.configurable else c }
      .flatMap { c -> if (c is ConfigurableWithOptionDescriptors) c.getOptionDescriptors(ID) { it } else emptyList() }
  }
}
