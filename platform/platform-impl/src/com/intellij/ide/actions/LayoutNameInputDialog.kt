// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages.InputDialog
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import org.jetbrains.annotations.ApiStatus
import javax.swing.Action

// a very ugly hack to work around the fact that we can neither
// access the myValidator field of the superclass (it's private)
// nor install the validator later (it must be passed to the constructor)
private var validatorBeingInitialized: LayoutNameValidator? = null
// no need for thread safety here as it's pure EDT code

@ApiStatus.Internal
class LayoutNameInputDialog(
  project: Project,
  @DialogMessage message: String,
  @DialogTitle title: String,
  @Button okButtonText: String,
  initialValue: String,
) : InputDialog(
  project,
  message,
  title,
  null,
  initialValue,
  LayoutNameValidator(),
) {

  private val validator = validatorBeingInitialized!! // initialized by the validator's constructor

  init {
    okAction.putValue(Action.NAME, okButtonText)
    validator.setErrorText = { text -> setErrorText(text) }
    validator.validationMoment = ValidationMoment.WHEN_TYPING // construction complete, user can type now
    validatorBeingInitialized = null // we don't need any leaks
  }

  override fun doOKAction() {
    validator.validationMoment = ValidationMoment.WHEN_OK_PRESSED
    try {
      super.doOKAction()
    }
    finally {
      validator.validationMoment = ValidationMoment.WHEN_TYPING // back to typing
    }
  }

}

private enum class ValidationMoment {
  WHEN_DIALOG_SHOWN,
  WHEN_TYPING,
  WHEN_OK_PRESSED,
}

private class LayoutNameValidator : InputValidator {

  lateinit var setErrorText: (@DialogMessage String?) -> Unit
  var validationMoment = ValidationMoment.WHEN_DIALOG_SHOWN // during construction

  init {
    validatorBeingInitialized = this
  }

  override fun checkInput(inputString: String?): Boolean {
    val manager: ToolWindowDefaultLayoutManager = ToolWindowDefaultLayoutManager.getInstance()
    val maxLength = Registry.intValue("ide.max.tool.window.layout.name.length", 50)

    val isEmpty: Boolean
    val isTooLong: Boolean
    val isAlreadyExisting: Boolean
    if (inputString.isNullOrBlank()) {
      isEmpty = true
      isTooLong = false
      isAlreadyExisting = false
    }
    else {
      isEmpty = false
      isTooLong = inputString.length > maxLength
      isAlreadyExisting = inputString in manager.getLayoutNames()
    }

    // According to the validation guidelines, https://plugins.jetbrains.com/docs/intellij/validation-errors.html
    // we just disable the OK button if the field is empty,
    // pop up an error message right away when the input is too long (and also disable the button),
    // and pop up an error message when the OK button is pressed if the layout already exists.
    val isValid = !isEmpty && !isTooLong && !isAlreadyExisting
    val shouldEnableOkButton = isValid || isAlreadyExisting
    val errorMessage = when {
      isTooLong -> IdeBundle.message("dialog.layout.name.too.long", maxLength)
      isAlreadyExisting -> IdeBundle.message("dialog.layout.already.exists")
      else -> null
    }
    return when (validationMoment) {
      ValidationMoment.WHEN_DIALOG_SHOWN -> {
        // setErrorText not initialized yet, but anyway we don't want to pop up an error message right away
        shouldEnableOkButton
      }
      ValidationMoment.WHEN_TYPING -> {
        setErrorText(if (isTooLong) errorMessage else null)
        shouldEnableOkButton
      }
      ValidationMoment.WHEN_OK_PRESSED -> {
        setErrorText(if (isAlreadyExisting) errorMessage else null)
        isValid // at this stage the return value determines whether the dialog will be closed and accepted
      }
    }
  }

  override fun canClose(inputString: String?): Boolean  = checkInput(inputString)

}
