// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

abstract class DialogPanelUnnamedConfigurableBase : UnnamedConfigurable {

  private lateinit var disposable: Disposable
  private lateinit var panel: DialogPanel

  final override fun createComponent(): JComponent {
    disposable = Disposer.newDisposable()
    panel = createPanel()
    panel.registerValidators(disposable)
    return panel
  }

  protected abstract fun createPanel(): DialogPanel

  final override fun getPreferredFocusedComponent(): JComponent? = panel.preferredFocusedComponent

  final override fun isModified(): Boolean = panel.isModified()

  final override fun apply(): Unit = panel.apply()

  final override fun reset(): Unit = panel.reset()

  final override fun cancel(): Unit = Unit

  final override fun disposeUIResources(): Unit = Disposer.dispose(disposable)
}
