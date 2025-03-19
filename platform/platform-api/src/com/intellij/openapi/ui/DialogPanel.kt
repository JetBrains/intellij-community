// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.components.JBPanel
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.awt.LayoutManager
import javax.swing.JComponent

class DialogPanel : JBPanel<DialogPanel> {
  var preferredFocusedComponent: JComponent? = null

  var validationRequestors: Map<JComponent, List<DialogValidationRequestor>> = emptyMap()
  var validationsOnInput: Map<JComponent, List<DialogValidation>> = emptyMap()
  var validationsOnApply: Map<JComponent, List<DialogValidation>> = emptyMap()

  var applyCallbacks: Map<JComponent?, List<() -> Unit>> = emptyMap()
  var resetCallbacks: Map<JComponent?, List<() -> Unit>> = emptyMap()
  var isModifiedCallbacks: Map<JComponent?, List<() -> Boolean>> = emptyMap()

  private var parentDisposable: Disposable? = null
  private var validator: DialogPanelValidator? = null

  private val integratedPanels = LinkedHashSet<DialogPanel>()
  private val integratedPanelRegisterListeners = DisposableWrapperList<(DialogPanel) -> Unit>()
  private val integratedPanelUnregisterListeners = DisposableWrapperList<(DialogPanel) -> Unit>()

  constructor() : super()
  constructor(layout: LayoutManager?) : super(layout)

  /**
   * [componentValidityChangedCallback] called on each [validationsOnInput]
   */
  fun registerValidators(
    parentDisposable: Disposable,
    componentValidityChangedCallback: ((Map<JComponent, ValidationInfo>) -> Unit)? = null
  ) {
    registerValidators(parentDisposable)

    val validator = validator
    if (componentValidityChangedCallback != null && validator != null) {
      validator.whenValidationStatusChanged(parentDisposable) {
        componentValidityChangedCallback(validator.getValidationStatus())
      }
    }
  }

  fun registerValidators(parentDisposable: Disposable) {
    this.parentDisposable = parentDisposable
    validator = DialogPanelValidator(this, parentDisposable)
  }

  /**
   * Calls [validationsOnApply] only (for [validationsOnInput] use [registerValidators] callback).
   * This function might be heavy and must be called before [apply] when user submits dialog
   */
  fun validateAll(): List<ValidationInfo> {
    return validator?.validateAll() ?: emptyList()
  }

  /**
   * To be called when user submits dialog
   * It is usually recommended to call [validateAll] and only call [apply] if no error
   */
  fun apply() {
    integratedPanels.forEach { it.apply() }

    for ((component, callbacks) in applyCallbacks.entries) {
      if (component == null) continue

      val modifiedCallbacks = isModifiedCallbacks[component]
      if (modifiedCallbacks.isNullOrEmpty() || modifiedCallbacks.any { it() }) {
        callbacks.forEach { it() }
      }
    }
    applyCallbacks[null]?.forEach { it() }
  }

  fun reset() {
    integratedPanels.forEach { it.reset() }

    for ((component, callbacks) in resetCallbacks.entries) {
      if (component == null) continue

      callbacks.forEach { it() }
    }
    resetCallbacks[null]?.forEach { it() }
  }

  fun isModified(): Boolean {
    return isModifiedCallbacks.values.any { list -> list.any { it() } } || integratedPanels.any { it.isModified() }
  }

  /**
   * Panels that should be integrated into [DialogPanel.apply]/[DialogPanel.reset]/
   * [DialogPanel.isModified] and validation mechanism.
   */
  @Internal
  fun getIntegratedPanels(): List<DialogPanel> {
    return integratedPanels.toList()
  }

  @Internal
  fun registerIntegratedPanel(panel: DialogPanel) {
    if (!integratedPanels.add(panel)) {
      throw Exception("Double registration of $panel")
    }
    integratedPanelRegisterListeners.forEach { it(panel) }
  }

  @Internal
  fun unregisterIntegratedPanel(panel: DialogPanel) {
    if (!integratedPanels.remove(panel)) {
      throw Exception("Panel is not registered: $panel")
    }
    integratedPanelUnregisterListeners.forEach { it(panel) }
  }

  @Internal
  fun whenIntegratedPanelRegistered(parentDisposable: Disposable, listener: (DialogPanel) -> Unit) {
    integratedPanelRegisterListeners.add(listener, parentDisposable)
  }

  @Internal
  fun whenIntegratedPanelUnregistered(parentDisposable: Disposable, listener: (DialogPanel) -> Unit) {
    integratedPanelUnregisterListeners.add(listener, parentDisposable)
  }

  @Deprecated("Use validateOnApply instead")
  @get:Deprecated("Use validateOnApply instead")
  @set:Deprecated("Use validateOnApply instead")
  @get:ScheduledForRemoval
  @set:ScheduledForRemoval
  @Suppress("DEPRECATION")
  var validateCallbacks: List<() -> ValidationInfo?>
    get() {
      val result = mutableListOf<() -> ValidationInfo?>()
      result.addAll(_validateCallbacks.map(::validateCallback))
      result.addAll(validationsOnApply.values.flatten().map(::validateCallback))
      result.addAll(integratedPanels.flatMap { it.validateCallbacks })
      return result
    }
    set(value) {
      _validateCallbacks = value.map(::validation)
    }

  @Suppress("PropertyName")
  internal var _validateCallbacks: List<DialogValidation> = emptyList()

  @Deprecated("Use validationsOnInput instead")
  @get:Deprecated("Use validationsOnInput instead")
  @set:Deprecated("Use validationsOnInput instead")
  @get:ScheduledForRemoval
  @set:ScheduledForRemoval
  @Suppress("DEPRECATION")
  var componentValidateCallbacks: Map<JComponent, () -> ValidationInfo?>
    get() = validationsOnInput.mapValues { validateCallback(it.value.first()) }
    set(value) {
      validationsOnInput = value.mapValues { listOf(validation(it.value)) }
    }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Migration function")
  @ScheduledForRemoval
  private fun validateCallback(validator: DialogValidation): () -> ValidationInfo? {
    return validator::validate
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Migration function")
  @ScheduledForRemoval
  private fun validation(validate: () -> ValidationInfo?) =
    DialogValidation { validate() }

  @Deprecated("Migration function")
  @ScheduledForRemoval
  private fun customValidationRequestor(requestor: DialogValidationRequestor): (() -> Unit) -> Unit {
    return { validate: () -> Unit -> requestor.subscribe(parentDisposable, validate) }
  }

  @Deprecated("Migration function")
  @ScheduledForRemoval
  private fun validationRequestor(requestor: (() -> Unit) -> Unit) =
    DialogValidationRequestor { _, it -> requestor(it) }
}
