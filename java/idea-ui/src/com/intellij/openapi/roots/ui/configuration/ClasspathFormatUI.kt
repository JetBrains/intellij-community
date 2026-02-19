// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer

internal class ClasspathFormatUI(
  providers: Array<ClasspathStorageProvider>,
  private val state: ModuleConfigurationState
) {
  private lateinit var comboBoxClasspathFormat: ComboBox<String>

  val panel = panel {
    val formatIdToDescription = mutableMapOf<String, String>()
    formatIdToDescription[ClassPathStorageUtil.DEFAULT_STORAGE] = JavaUiBundle.message("project.roots.classpath.format.default.descr")
    for (provider in providers) {
      formatIdToDescription[provider.getID()] = provider.getDescription()
    }

    row(JavaUiBundle.message("project.roots.classpath.format.label")) {
      comboBoxClasspathFormat = comboBox(formatIdToDescription.keys, textListCellRenderer { item -> formatIdToDescription[item] })
        .applyToComponent { selectedItem = this@ClasspathFormatUI.moduleClasspathFormat }
        .component
    }
  }

  val selectedClasspathFormat: String
    get() = comboBoxClasspathFormat.selectedItem as String

  val moduleClasspathFormat: @NlsSafe String
    get() = ClassPathStorageUtil.getStorageType(state.getCurrentRootModel().getModule())

  val isModified: Boolean
    get() = this.selectedClasspathFormat != this.moduleClasspathFormat

  @Throws(ConfigurationException::class)
  fun canApply() {
    val provider: ClasspathStorageProvider? = ClasspathStorage.getProvider(this.selectedClasspathFormat)
    provider?.assertCompatible(state.getCurrentRootModel())
  }

  @Throws(ConfigurationException::class)
  fun apply() {
    canApply()
    ClasspathStorage.setStorageType(state.getCurrentRootModel(), this.selectedClasspathFormat)
  }
}