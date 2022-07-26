// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SpinningProgressIcon
import com.intellij.ui.components.fields.ExtendableTextComponent

class TextCompletionComboBoxWithSpinner<T>(project: Project?, converter: TextCompletionComboBoxConverter<T>) : TextCompletionComboBox<T>(
  project, converter) {

  private val spinner = SpinningProgressIcon()
  private val myExtension: ExtendableTextComponent.Extension

  var spinning: Boolean = false
    set(value) {
      if (value) {
        addExtension(myExtension)
      }
      else {
        removeExtension(myExtension)
      }
    }
    get() = field

  init {
    this.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    myExtension = ExtendableTextComponent.Extension.create(spinner, spinner, null, { })

  }
}