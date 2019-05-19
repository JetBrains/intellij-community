// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.openapi.ui.DialogPanel
import javax.swing.JComponent

/**
 * @author yole
 */
abstract class BoundConfigurable(private val displayName: String, private val helpTopic: String? = null) : Configurable {
  private val panel: DialogPanel by lazy { createPanel() }

  abstract fun createPanel(): DialogPanel

  final override fun createComponent(): JComponent? = panel

  override fun isModified() = panel.isModified()

  override fun getDisplayName(): String = displayName

  override fun reset() {
    panel.reset()
  }

  override fun apply() {
    panel.apply()
  }

  override fun getHelpTopic(): String? = helpTopic
}