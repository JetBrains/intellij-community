// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @author yole
 */
class DialogPanel : JPanel {
  var preferredFocusedComponent: JComponent? = null
  var validateCallbacks: List<() -> ValidationInfo?> = emptyList()
  var applyCallbacks: List<() -> Unit> = emptyList()
  var resetCallbacks: List<() -> Unit> = emptyList()
  var isModifiedCallbacks: List<() -> Boolean> = emptyList()

  constructor() : super()
  constructor(layout: LayoutManager?) : super(layout)

  fun apply() {
    for (applyCallback in applyCallbacks) {
      applyCallback()
    }
  }

  fun reset() {
    for (resetCallback in resetCallbacks) {
      resetCallback()
    }
  }

  fun isModified(): Boolean {
    return isModifiedCallbacks.any { it() }
  }
}
