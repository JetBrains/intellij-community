// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.ClearableLazyValue
import javax.swing.JComponent

/**
 * @author yole
 */
abstract class BoundConfigurable(private val displayName: String, private val helpTopic: String? = null) : Configurable {
  private val panel = object : ClearableLazyValue<DialogPanel>() {
    override fun compute() = createPanel()
  }

  abstract fun createPanel(): DialogPanel

  final override fun createComponent(): JComponent? = panel.value

  override fun isModified() = panel.value.isModified()

  override fun getDisplayName(): String = displayName

  override fun reset() {
    panel.value.reset()
  }

  override fun apply() {
    panel.value.apply()
  }

  override fun disposeUIResources() {
    panel.drop()
  }

  override fun getHelpTopic(): String? = helpTopic
}

abstract class BoundCompositeConfigurable<T : UnnamedConfigurable>(
  displayName: String,
  helpTopic: String? = null
) : BoundConfigurable(displayName, helpTopic) {
  private var configurablesCreated = false

  abstract fun createConfigurables(): List<T>

  protected val configurables by lazy {
    configurablesCreated = true
    createConfigurables()
  }

  override fun isModified(): Boolean {
    return super.isModified() || configurables.any { it.isModified }
  }

  override fun reset() {
    super.reset()
    for (configurable in configurables) {
      configurable.reset()
    }
  }

  override fun apply() {
    super.apply()
    for (configurable in configurables) {
      configurable.apply()
    }
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    if (configurablesCreated) {
      for (configurable in configurables) {
        configurable.disposeUIResources()
      }
    }
  }
}
