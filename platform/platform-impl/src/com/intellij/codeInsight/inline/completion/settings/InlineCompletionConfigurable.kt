// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.settings

import com.intellij.application.options.editor.EditorOptionsProvider
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.Box

class InlineCompletionConfigurable : BoundCompositeConfigurable<UnnamedConfigurable>(
  ApplicationBundle.message("title.inline.completion"),
  null // TODO
), EditorOptionsProvider, WithEpDependencies {

  override fun getId(): String = ID

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
    return listOf(InlineCompletionConfigurableEP.EP_NAME)
  }

  override fun createConfigurables(): List<UnnamedConfigurable> {
    return ConfigurableWrapper.createConfigurables(InlineCompletionConfigurableEP.EP_NAME)
  }

  override fun createPanel(): DialogPanel {
    return panel {
      configurables.forEachIndexed { index, configurable ->
        appendDslConfigurable(configurable)
        if (index != configurables.lastIndex) {
          repeat(2) {
            row {
              cell(Box.Filler(JBUI.size(0), JBUI.size(0), JBUI.size(Integer.MAX_VALUE, 0)))
            }
          }
        }
      }
    }
  }

  companion object {
    const val ID: String = "editor.preferences.inline.completion"
  }
}