// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.util.function.Supplier

fun SubmittableTextField.withValidation(): SubmittableTextField {
  UiNotifyConnector(this, ValidatorActivatable(submittableModel, this), false)
  return this
}

private class ValidatorActivatable(
  private val model: SubmittableTextFieldModel,
  private val textField: EditorTextField
) : Activatable {
  private var validatorDisposable: Disposable? = null
  private var validator: ComponentValidator? = null

  init {
    model.addStateListener {
      validator?.revalidate()
    }
  }

  override fun showNotify() {
    validatorDisposable = Disposer.newDisposable("ETF validator")
    validator = ComponentValidator(validatorDisposable!!).withValidator(Supplier {
      model.error?.let { ValidationInfo(it.message.orEmpty(), textField) }
    }).installOn(textField)
  }

  override fun hideNotify() {
    validatorDisposable?.let { Disposer.dispose(it) }
    validatorDisposable = null
    validator = null
  }
}