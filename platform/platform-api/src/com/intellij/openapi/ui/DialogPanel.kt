// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBPanel
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus
import java.awt.LayoutManager
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.text.JTextComponent

class DialogPanel : JBPanel<DialogPanel> {
  var preferredFocusedComponent: JComponent? = null

  var validationRequestors: Map<JComponent, List<DialogValidationRequestor>> = emptyMap()
  var validationsOnInput: Map<JComponent, List<DialogValidation>> = emptyMap()
  var validationsOnApply: Map<JComponent, List<DialogValidation>> = emptyMap()

  var applyCallbacks: Map<JComponent?, List<() -> Unit>> = emptyMap()
  var resetCallbacks: Map<JComponent?, List<() -> Unit>> = emptyMap()
  var isModifiedCallbacks: Map<JComponent?, List<() -> Boolean>> = emptyMap()

  private var parentDisposable: Disposable? = null
  private val integratedPanels = mutableMapOf<DialogPanel, Disposable?>()

  private val applyValidationRequestor = ApplyValidationRequestor()
  private val componentValidationStatus = hashMapOf<JComponent, ValidationInfo>()
  private val validationCallbacks = DisposableWrapperList<(Map<JComponent, ValidationInfo>) -> Unit>()

  constructor() : super()
  constructor(layout: LayoutManager?) : super(layout)

  fun registerValidators(
    parentDisposable: Disposable,
    componentValidityChangedCallback: ((Map<JComponent, ValidationInfo>) -> Unit)? = null
  ) {
    if (componentValidityChangedCallback != null) {
      validationCallbacks.add(componentValidityChangedCallback, parentDisposable)
    }
    registerValidators(parentDisposable)
  }

  fun registerValidators(parentDisposable: Disposable) {
    this.parentDisposable = parentDisposable
    registerValidatorsForIntegratedPanels(integratedPanels.keys)

    for (component in validationsOnInput.keys + validationsOnApply.keys) {
      val validator = ComponentValidator(parentDisposable)
      registerValidators(component, validator)
      registerValidationRequestors(component, validator, parentDisposable)
      validator.installOn(component)
    }
  }

  private fun registerValidators(component: JComponent, validator: ComponentValidator) {
    validator.withValidator(Supplier {
      val validations = if (applyValidationRequestor.isActive) validationsOnApply else validationsOnInput
      val validationInfo = validations[component]?.let(::applyValidators)
      fireValidationStatusChanged(component, validationInfo)
      validationInfo
    })
  }

  private fun applyValidators(validations: List<DialogValidation>): ValidationInfo? {
    var result: ValidationInfo? = null
    for (validation in validations) {
      val validationInfo = validation.validate()
      if (validationInfo != null) {
        if (!validationInfo.okEnabled) {
          return validationInfo
        }
        if (result == null || !validationInfo.warning) {
          result = validationInfo
        }
      }
    }
    return result
  }

  private fun registerValidationRequestors(component: JComponent, validator: ComponentValidator, parentDisposable: Disposable) {
    val validationRequestors = validationRequestors[component] ?: emptyList()
    if (validationRequestors.isEmpty() && component is JTextComponent) {
      validator.andRegisterOnDocumentListener(component)
    }
    for (validationRequestor in validationRequestors) {
      validationRequestor.subscribe(parentDisposable, validator::revalidate)
    }
    applyValidationRequestor.subscribe(parentDisposable, validator::revalidate)
  }

  private fun fireValidationStatusChanged(component: JComponent, validationInfo: ValidationInfo?) {
    if (componentValidationStatus[component] != validationInfo) {
      if (validationInfo != null) {
        componentValidationStatus[component] = validationInfo
        logValidationInfoInHeadlessMode(validationInfo)
      }
      else {
        componentValidationStatus.remove(component)
      }
      validationCallbacks.forEach { it(componentValidationStatus) }
    }
  }

  private fun logValidationInfoInHeadlessMode(info: ValidationInfo) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      logger<DialogPanel>().warn(info.message)
    }
  }

  fun validateAll(): List<ValidationInfo> {
    applyValidationRequestor.validateAll()
    val panelValidationInfo = _validateCallbacks.mapNotNull { it.validate() }
    val integratedPanelValidationInfo = integratedPanels.keys.flatMap { it.validateAll() }
    return componentValidationStatus.values + panelValidationInfo + integratedPanelValidationInfo
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

  /**
   * Registers panel that should be integrated into [DialogPanel.apply]/[DialogPanel.reset]/
   * [DialogPanel.isModified] and validation mechanism
   *
   * @see unregisterSubPanel
   */
  @ApiStatus.Internal
  fun registerSubPanel(panel: DialogPanel) {
    if (integratedPanels.contains(panel)) {
      throw Exception("Double registration of $panel")
    }
    integratedPanels[panel] = null
    registerValidatorsForIntegratedPanels(setOf(panel))
  }

  /**
   * @see registerSubPanel
   */
  @ApiStatus.Internal
  fun unregisterSubPanel(panel: DialogPanel) {
    if (!integratedPanels.contains(panel)) {
      throw Exception("Panel is not registered: $panel")
    }
    val disposable = integratedPanels.remove(panel)
    disposable?.let { Disposer.dispose(it) }
  }

  private fun registerValidatorsForIntegratedPanels(panels: Set<DialogPanel>) {
    parentDisposable?.let {
      for (panel in panels) {
        val disposable = Disposer.newDisposable()
        integratedPanels.put(panel, disposable)?.let { oldDisposable -> Disposer.dispose(oldDisposable) }
        Disposer.register(it, disposable)
        panel.registerValidators(disposable)
      }
    }
  }

  private class ApplyValidationRequestor : DialogValidationRequestor {
    private val validators = DisposableWrapperList<() -> Unit>()

    var isActive = false
      private set

    fun validateAll() {
      isActive = true
      try {
        validators.forEach { it() }
      }
      finally {
        isActive = false
      }
    }

    override fun subscribe(parentDisposable: Disposable?, validate: () -> Unit) {
      if (parentDisposable == null) {
        validators.add(validate)
      }
      else {
        validators.add(validate, parentDisposable)
      }
    }
  }

  @Deprecated("Use validateOnApply instead")
  var validateCallbacks: List<() -> ValidationInfo?>
    get() {
      val result = mutableListOf<() -> ValidationInfo?>()
      result.addAll(_validateCallbacks.map(::validateCallback))
      result.addAll(validationsOnApply.values.flatten().map(::validateCallback))
      result.addAll(integratedPanels.keys.flatMap { it.validateCallbacks })
      return result
    }
    set(value) {
      _validateCallbacks = value.map(::validation)
    }

  private var _validateCallbacks: List<DialogValidation> = emptyList()

  @Deprecated("Use registerValidators instead")
  var componentValidateCallbacks: Map<JComponent, () -> ValidationInfo?>
    get() = validationsOnInput.mapValues { validateCallback(it.value.first()) }
    set(value) {
      validationsOnInput = value.mapValues { listOf(validation(it.value)) }
    }

  @Deprecated("Use registerValidators instead")
  var customValidationRequestors: Map<JComponent, List<(() -> Unit) -> Unit>>
    get() = validationRequestors.mapValues { it.value.map(::customValidationRequestor) }
    set(value) {
      validationRequestors = value.mapValues { it.value.map(::validationRequestor) }
    }

  private fun validateCallback(validator: DialogValidation): () -> ValidationInfo? {
    return validator::validate
  }

  private fun validation(validate: () -> ValidationInfo?) =
    DialogValidation { validate() }

  private fun customValidationRequestor(requestor: DialogValidationRequestor): (() -> Unit) -> Unit {
    return { validate: () -> Unit -> requestor.subscribe(parentDisposable, validate) }
  }

  private fun validationRequestor(requestor: (() -> Unit) -> Unit) =
    DialogValidationRequestor { _, it -> requestor(it) }
}
