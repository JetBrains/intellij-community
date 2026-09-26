// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

import com.intellij.codeInsight.documentation.getDocumentationFontSize
import com.intellij.codeInsight.documentation.setDocumentationFontSize
import com.intellij.openapi.options.FontSize
import com.intellij.ui.FontSizeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class DocumentationFontSizeModel : FontSizeModel<FontSize> {

  private val myFontSize: MutableStateFlow<FontSize> = MutableStateFlow(getDocumentationFontSize()) // initialize with current state

  override var value: FontSize
    get() {
      return myFontSize.value
    }
    set(value) {
      setDocumentationFontSize(value) // persist modifications
      myFontSize.value = value
    }

  override val values: List<FontSize>
    get() = FontSize.entries.toList()

  override val updates: Flow<FontSize> = myFontSize.asSharedFlow()
}
