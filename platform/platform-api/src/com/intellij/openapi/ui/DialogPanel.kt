// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.LayoutManager
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.text.JTextComponent

/**
 * Client property for children with class [DialogPanel] that should be integrated into parents [DialogPanel.apply]/[DialogPanel.reset]/
 * [DialogPanel.isModified] and validation mechanism. The property must be set before adding into parent
 */
@ApiStatus.Internal
const val INTEGRATED_PANEL_PROPERTY = "DialogPanel.child"

class DialogPanel : JBPanel<DialogPanel> {
  var preferredFocusedComponent: JComponent? = null

  /**
   * Returns union with [integratedPanels] validateCallbacks
   */
  var validateCallbacks: List<() -> ValidationInfo?>
    get() {
      val result = mutableListOf<() -> ValidationInfo?>()
      result.addAll(_validateCallbacks)
      result.addAll(integratedPanels.keys.flatMap { it.validateCallbacks })
      return result
    }
    set(value) {
      _validateCallbacks = value
    }

  var componentValidateCallbacks: Map<JComponent, () -> ValidationInfo?> = emptyMap()
  var customValidationRequestors: Map<JComponent, List<(() -> Unit) -> Unit>> = emptyMap()
  var applyCallbacks: Map<JComponent?, List<() -> Unit>> = emptyMap()
  var resetCallbacks: Map<JComponent?, List<() -> Unit>> = emptyMap()
  var isModifiedCallbacks: Map<JComponent?, List<() -> Boolean>> = emptyMap()

  private var parentDisposable: Disposable? = null
  private var componentValidityChangedCallback: ((Map<JComponent, ValidationInfo>) -> Unit)? = null
  private val integratedPanels = mutableMapOf<DialogPanel, Disposable>()
  private var _validateCallbacks: List<() -> ValidationInfo?> = emptyList()

  private val componentValidationStatus = hashMapOf<JComponent, ValidationInfo>()

  constructor() : super()
  constructor(layout: LayoutManager?) : super(layout)

  fun registerValidators(parentDisposable: Disposable, componentValidityChangedCallback: ((Map<JComponent, ValidationInfo>) -> Unit)? = null) {
    this.parentDisposable = parentDisposable
    this.componentValidityChangedCallback = componentValidityChangedCallback
    registerIntegratedPanels(integratedPanels)

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
    integratedPanels.keys.forEach { it.apply() }

    for ((component, callbacks) in applyCallbacks.entries) {
      if (component == null) continue

      val modifiedCallbacks = isModifiedCallbacks.get(component)
      if (modifiedCallbacks.isNullOrEmpty() || modifiedCallbacks.any { it() }) {
        callbacks.forEach { it() }
      }
    }
    applyCallbacks.get(null)?.forEach { it() }
  }

  fun reset() {
    integratedPanels.keys.forEach { it.reset() }

    for ((component, callbacks) in resetCallbacks.entries) {
      if (component == null) continue

      callbacks.forEach { it() }
    }
    resetCallbacks.get(null)?.forEach { it() }
  }

  fun isModified(): Boolean {
    return isModifiedCallbacks.values.any { list -> list.any { it() } } || integratedPanels.keys.any { it.isModified() }
  }

  override fun addImpl(comp: Component?, constraints: Any?, index: Int) {
    super.addImpl(comp, constraints, index)
    if (comp is DialogPanel && comp.getClientProperty(INTEGRATED_PANEL_PROPERTY) != null) {
      val disposable = Disposer.newDisposable()
      integratedPanels[comp] = disposable
      registerIntegratedPanels(mapOf(comp to disposable))
    }
  }

  override fun remove(index: Int) {
    val comp = getComponent(index)
    val disposable = integratedPanels.remove(comp)
    disposable?.let { Disposer.dispose(it) }
    super.remove(index)
  }

  override fun removeAll() {
    integratedPanels.values.forEach { Disposer.dispose(it) }
    integratedPanels.clear()
    super.removeAll()
  }

  private fun registerIntegratedPanels(panels: Map<DialogPanel, Disposable>) {
    parentDisposable?.let {
      for ((panel, disposable) in panels) {
        Disposer.register(it, disposable)
        panel.registerValidators(disposable, componentValidityChangedCallback)
      }
    }
  }

  private fun registerCustomValidationRequestors(component: JComponent, validator: ComponentValidator) {
    for (onCustomValidationRequest in customValidationRequestors.get(component) ?: return) {
      onCustomValidationRequest { validator.revalidate() }
    }
  }
}
