// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import javax.swing.SwingUtilities

class NonProportionalOnePixelSplitter(
  vertical: Boolean,
  proportionKey: String,
  defaultProportion: Float = 0.5f,
  disposable: Disposable
) : OnePixelSplitter(vertical, proportionKey, defaultProportion) {

  init {
    Disposer.register(disposable, Disposable {
      saveProportion()
    })

    dividerPositionStrategy = DividerPositionStrategy.KEEP_FIRST_SIZE
  }

  override fun addNotify() {
    super.addNotify()
    dividerPositionStrategy = DividerPositionStrategy.KEEP_PROPORTION
    SwingUtilities.invokeLater {
      loadProportion()
      dividerPositionStrategy = DividerPositionStrategy.KEEP_FIRST_SIZE
    }
  }

  override fun setProportion(proportion: Float) {
    if (proportion < 0 || proportion > 1) return

    super.setProportion(proportion)
  }

  public override fun saveProportion() {
    super.saveProportion()
  }
}