// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.popup.PopupFactoryImpl

internal fun getListCellRendererComponent() = listCellRenderer<PopupFactoryImpl.ActionItem> {
  val action = value.action
  if (action is HighlightingChooser.HighlightAction) {
    val actionAttributes = action.textAttributes
    background = actionAttributes.backgroundColor
    text(action.templateText) {
      attributes = SimpleTextAttributes.fromTextAttributes(actionAttributes)
    }
  } else {
    separator {}
    text(action.templateText)
  }
}