// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.text.JTextComponent

typealias ValidationStatus = Map<JComponent, ValidationInfo>
typealias MutableValidationStatus = MutableMap<JComponent, ValidationInfo>

@ApiStatus.Internal
internal class DialogPanelValidator(panel: DialogPanel, parentDisposable: Disposable) {

  private val panels = DisposableWrapperList<DialogPanel>()
  private val validationStatus = DisposableWrapperList<ValidationStatus>()
  private val validationListeners = DisposableWrapperList<() -> Unit>()
  private val applyValidationRequestor = ApplyValidationRequestor()

  init {
    registerPanel(panel, parentDisposable)
  }

  private fun registerPanel(panel: DialogPanel, parentDisposable: Disposable) {
    panels.add(panel, parentDisposable)
    registerIntegratedPanels(panel, parentDisposable)
    registerValidators(panel, parentDisposable)
  }

  private fun registerIntegratedPanels(parent: DialogPanel, parentDisposable: Disposable) {
    for (child in parent.getIntegratedPanels()) {
      registerIntegratedPanel(parent, child, parentDisposable)
    }
    parent.whenIntegratedPanelRegistered(parentDisposable) {
      registerIntegratedPanel(parent, it, parentDisposable)
    }
  }

  private fun registerIntegratedPanel(parent: DialogPanel, child: DialogPanel, parentDisposable: Disposable) {
    val disposable = Disposer.newDisposable(parentDisposable, "Integrated panel disposable for $child")
    parent.whenIntegratedPanelUnregistered(parentDisposable) {
      if (it === child) {
        Disposer.dispose(disposable)
      }
    }
    registerPanel(child, disposable)
  }

  private fun registerValidators(panel: DialogPanel, parentDisposable: Disposable) {
    val components = panel.validationsOnInput.keys + panel.validationsOnApply.keys
    for (component in components) {
      val validator = ComponentValidator(parentDisposable)
      registerValidator(validator, panel, component, parentDisposable)
      registerValidationRequestors(panel, component, validator, parentDisposable)
      validator.installOn(component)
    }
  }

  private fun registerValidator(validator: ComponentValidator, panel: DialogPanel, component: JComponent, parentDisposable: Disposable) {
    val validationStatus = LinkedHashMap<JComponent, ValidationInfo>()
    this.validationStatus.add(validationStatus, parentDisposable)
    validator.withValidator(Supplier {
      applyValidations(panel, component, validationStatus)
    })
  }

  private fun applyValidations(panel: DialogPanel, component: JComponent, validationStatus: MutableValidationStatus): ValidationInfo? {
    val validations = when (applyValidationRequestor.isActive) {
      true -> panel.validationsOnApply[component] ?: emptyList()
      else -> panel.validationsOnInput[component] ?: emptyList()
    }
    val validationInfo = applyValidations(validations)
    if (validationStatus[component] != validationInfo) {
      if (validationInfo != null) {
        validationStatus[component] = validationInfo
        logValidationInfoInHeadlessMode(validationInfo)
      }
      else {
        validationStatus.remove(component)
      }
      fireValidationStatusChanged()
    }
    return validationInfo
  }

  private fun applyValidations(validations: List<DialogValidation>): ValidationInfo? {
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

  private fun logValidationInfoInHeadlessMode(info: ValidationInfo) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      logger<DialogPanel>().warn(info.message)
    }
  }

  fun getValidationStatus(): ValidationStatus {
    return validationStatus
      .flatMap { it.entries }
      .associate { it.key to it.value }
  }

  fun whenValidationStatusChanged(parentDisposable: Disposable, listener: () -> Unit) {
    validationListeners.add(listener, parentDisposable)
  }

  private fun fireValidationStatusChanged() {
    validationListeners.forEach { it() }
  }

  private fun registerValidationRequestors(
    panel: DialogPanel,
    component: JComponent,
    validator: ComponentValidator,
    parentDisposable: Disposable
  ) {
    val validationRequestors = panel.validationRequestors[component] ?: emptyList()
    if (validationRequestors.isEmpty() && component is JTextComponent) {
      validator.andRegisterOnDocumentListener(component)
    }
    for (validationRequestor in validationRequestors) {
      validationRequestor.subscribe(parentDisposable, validator::revalidate)
    }
    applyValidationRequestor.subscribe(parentDisposable, validator::revalidate)
  }

  private fun showComponentWithInvalidData() {
    val components = getValidationStatus().entries.asSequence()
      .filter { !it.value.okEnabled }
      .map { it.key }
      .toList()
    if (components.all { !it.isShowing }) {
      for (component in components) {
        UiSwitcher.show(component)
      }
    }
  }

  fun validateAll(): List<ValidationInfo> {
    applyValidationRequestor.validateAll()
    val validationInfoList = panels.asSequence()
      .flatMap { it._validateCallbacks }
      .mapNotNull { it.validate() }

    showComponentWithInvalidData()

    return getValidationStatus().values + validationInfoList
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
}