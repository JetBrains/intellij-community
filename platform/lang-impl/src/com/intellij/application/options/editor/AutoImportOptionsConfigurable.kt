// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.Configurable.VariableProjectAppLevel
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel

class AutoImportOptionsConfigurable(private val project: Project) :
  BoundCompositeConfigurable<AutoImportOptionsProvider>(
    ApplicationBundle.message("auto.import"), "reference.settingsdialog.IDE.editor.autoimport"),
  EditorOptionsProvider, VariableProjectAppLevel, WithEpDependencies {

  override fun createConfigurables(): List<AutoImportOptionsProvider> {
    return AutoImportOptionsProviderEP.EP_NAME.getExtensions(project).mapNotNull { it.createConfigurable() }
  }

  override fun isProjectLevel(): Boolean {
    return false
  }

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
    return setOf<BaseExtensionPointName<*>>(AutoImportOptionsProviderEP.EP_NAME)
  }

  override fun getId(): String {
    return "editor.preferences.import"
  }

  override fun createPanel(): DialogPanel {
    return panel {
      for (configurable in configurables) {
        appendDslConfigurable(configurable)
      }
    }
  }
}
