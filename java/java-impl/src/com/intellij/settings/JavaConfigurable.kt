// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import com.intellij.java.JavaBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class JavaConfigurable(project: Project) : SearchableConfigurable, NoScroll {
  private val delegate = JavaConfigurablePanel(project, project.service<JavaSettingsStorage>().state)

  override fun getDisplayName(): String = JavaBundle.message("java.configurable.display.name")

  override fun getId(): String = JavaBundle.message("java.configurable.id")

  override fun createComponent(): JComponent = delegate.getMainPanel()

  override fun isModified(): Boolean = delegate.isModified()

  override fun reset() = delegate.reset()

  override fun apply() = delegate.apply()

  override fun disposeUIResources() = delegate.dispose()
}
