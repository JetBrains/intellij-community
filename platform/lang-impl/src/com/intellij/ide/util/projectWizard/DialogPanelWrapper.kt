// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo

/**
 * [com.intellij.openapi.ui.DialogPanel] reports input errors using callbacks of [com.intellij.openapi.ui.DialogPanel.registerValidators],
 * and doesn't report them when [com.intellij.openapi.ui.DialogPanel.validateAll] is called.
 *
 * On each callback we store errors in [setInputValidationError]
 * [inputValidationError] is checked frequently as light (on input) validation
 * [applyOrGetError] is called when user submits the dialog
 *
 * This class is only for [ProjectSettingsStepBase]
 */
internal class DialogPanelWrapper(private val dialogPanel: DialogPanel) {
  var inputValidationError: ValidationInfo? = null
    private set

  fun setInputValidationError(infos: Collection<ValidationInfo>) {
    inputValidationError = findError(infos)
  }

  /**
   * To be called when a user submits the dialog. Returns error or null if no error.
   */
  fun applyOrGetError(): ValidationInfo? {
    val applyError =  findError(dialogPanel.validateAll())
    if (applyError != null) return applyError
    dialogPanel.apply()
    return null
  }

  private fun findError(infos: Collection<ValidationInfo>): ValidationInfo? =
    // Errors might be unsorted, not to display different error each time we sort them.
    infos.sortedBy { it.message }.firstOrNull { !it.okEnabled }
}