// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBPanel
import com.intellij.util.containers.MultiMap
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
  var customValidationRequestors: MultiMap<JComponent, (() -> Unit) -> Unit> = MultiMap.empty()
  var applyCallbacks: MultiMap<JComponent?, () -> Unit> = MultiMap.empty()
  var resetCallbacks: MultiMap<JComponent?, () -> Unit> = MultiMap.empty()
  var isModifiedCallbacks: MultiMap<JComponent?, () -> Boolean> = MultiMap.empty()

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
      registerCustomValidationRequestors(component, validator)
      validator.installOn(component)
    }
  }

  fun apply() {
    for ((component, callbacks) in applyCallbacks.entrySet()) {
      if (component == null) continue

      val modifiedCallbacks = isModifiedCallbacks.get(component)
      if (modifiedCallbacks.isEmpty() || modifiedCallbacks.any { it() }) {
        callbacks.forEach { it() }
      }
    }
    applyCallbacks[null].forEach { it() }
  }

  fun reset() {
    for ((component, callbacks) in resetCallbacks.entrySet()) {
      if (component == null) continue

      callbacks.forEach { it() }
    }
    resetCallbacks[null].forEach { it() }
  }

  fun isModified(): Boolean {
    return isModifiedCallbacks.values().any { it() }
  }

  private fun registerCustomValidationRequestors(component: JComponent, validator: ComponentValidator) {
    for (onCustomValidationRequest in customValidationRequestors[component]) {
      onCustomValidationRequest { validator.revalidate() }
    }
  }
}
