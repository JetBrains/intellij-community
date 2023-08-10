// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.ui.components.panels.Wrapper
import javax.swing.JComponent
import javax.swing.border.Border

internal class DynamicBorderWrapper(wrapped: JComponent, borderSupplier: () -> Border) : Wrapper(wrapped) {

  private var borderSupplier: (() -> Border)? = null // overridable-call-from-constructor workaround, see below

  init {
    border = borderSupplier()
    this.borderSupplier = borderSupplier
  }

  override fun updateUI() {
    super.updateUI()
    border = borderSupplier?.invoke() // if updateUI() is called from a super constructor, borderSupplier will be null here
  }

}
