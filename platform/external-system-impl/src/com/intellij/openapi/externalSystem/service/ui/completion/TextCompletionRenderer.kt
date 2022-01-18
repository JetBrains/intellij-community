// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.ui.SimpleColoredComponent
import javax.swing.JList

interface TextCompletionRenderer<T> {

  fun getText(item: T): String

  fun customizeCellRenderer(editor: TextCompletionField<T>, cell: Cell<T>)

  data class Cell<T>(
    val component: SimpleColoredComponent,
    val item: T,
    val list: JList<*>,
    val index: Int,
    val isSelected: Boolean,
    val hasFocus: Boolean
  )
}