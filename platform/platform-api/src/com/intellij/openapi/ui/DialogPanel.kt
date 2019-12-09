// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBPanel
import java.awt.LayoutManager
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.text.JTextComponent

/**
 * @author yole
 */
class DialogPanel : JBPanel<DialogPanel> {
  var preferredFocusedComponent: JComponent? = null
  var validateCallbacks: List<() -> ValidationInfo?> = emptyList()
  var componentValidateCallbacks: Map<JComponent, () -> ValidationInfo?> = emptyMap()
  var applyCallbacks: List<() -> Unit> = emptyList()
  var resetCallbacks: List<() -> Unit> = emptyList()
  var isModifiedCallbacks: List<() -> Boolean> = emptyList()

  private val componentValidationStatus = hashMapOf<JComponent, ValidationInfo>()

  constructor() : super()
  constructor(layout: LayoutManager?) : super(layout)

  fun registerValidators(parentDisposable: Disposable, componentValidityChangedCallback: ((Map<JComponent, ValidationInfo>) -> Unit)? = null) {
    for ((component, callback) in componentValidateCallbacks) {
      val validator = ComponentValidator(parentDisposable).withValidator(Supplier {
        val infoForComponent = callback()
        if (componentValidationStatus[component] != infoForComponent) {
          if (infoForComponent != null) {
            componentValidationStatus[component] = infoForComponent
          }
          else {
            componentValidationStatus.remove(component)
          }
          componentValidityChangedCallback?.invoke(componentValidationStatus)
        }
        infoForComponent
      })
      if (component is JTextComponent) {
        validator.andRegisterOnDocumentListener(component)
      }
      validator.installOn(component)
    }
  }

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
