package com.intellij

import com.intellij.application.options.CodeStyleConfigurableWrapper
import com.intellij.application.options.CodeStyleSchemesConfigurable
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.application.options.codeStyle.CodeStyleSettingsPanelFactory
import com.intellij.application.options.codeStyle.NewCodeStyleSettingsPanel
import com.intellij.ide.todo.configurable.TodoConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider

/**
 * Created by Kirill.Skrygan on 7/4/2016.
 */

open class ConfigurableFactory : Disposable {
    companion object {
        fun getInstance(): ConfigurableFactory {
            return ServiceManager.getService(ConfigurableFactory::class.java)
        }
    }

    override fun dispose() {
    }

    open fun createCodeStyleConfigurable(provider: CodeStyleSettingsProvider, codeStyleSchemesModel: CodeStyleSchemesModel, owner: CodeStyleSchemesConfigurable): CodeStyleConfigurableWrapper {
        val codeStyleConfigurableWrapper = CodeStyleConfigurableWrapper(provider, object : CodeStyleSettingsPanelFactory() {
            override fun createPanel(scheme: CodeStyleScheme): NewCodeStyleSettingsPanel {
                return NewCodeStyleSettingsPanel(provider.createSettingsPage(scheme.codeStyleSettings, codeStyleSchemesModel.getCloneSettings(scheme)))
            }
        }, owner)
        return codeStyleConfigurableWrapper
    }

    open fun getTodoConfigurable(): TodoConfigurable {
        return TodoConfigurable()
    }
}