// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij

import com.intellij.application.options.CodeStyleConfigurableWrapper
import com.intellij.application.options.CodeStyleSchemesConfigurable
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.application.options.codeStyle.CodeStyleSettingsPanelFactory
import com.intellij.application.options.codeStyle.NewCodeStyleSettingsPanel
import com.intellij.ide.todo.configurable.TodoConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider

open class ConfigurableFactory : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(): ConfigurableFactory {
      return ApplicationManager.getApplication().getService(ConfigurableFactory::class.java)
    }
  }

  override fun dispose() {
  }

  open fun createCodeStyleConfigurable(provider: CodeStyleSettingsProvider,
                                       codeStyleSchemesModel: CodeStyleSchemesModel,
                                       owner: CodeStyleSchemesConfigurable): CodeStyleConfigurableWrapper {
    val codeStyleConfigurableWrapper = CodeStyleConfigurableWrapper(provider, object : CodeStyleSettingsPanelFactory() {
      override fun createPanel(scheme: CodeStyleScheme): NewCodeStyleSettingsPanel {
        return NewCodeStyleSettingsPanel(
          provider.createConfigurable(scheme.codeStyleSettings, codeStyleSchemesModel.getCloneSettings(scheme)), codeStyleSchemesModel)
      }
    }, owner)
    return codeStyleConfigurableWrapper
  }

  open fun getTodoConfigurable(project: Project): TodoConfigurable {
    return TodoConfigurable()
  }
}