// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import javax.swing.ListCellRenderer

@Deprecated("See details in ExternalSystemJdkComboBox")
internal fun externalSystemJdkComboBoxRenderer(): ListCellRenderer<ExternalSystemJdkComboBox.JdkComboBoxItem?> {
  return listCellRenderer("") {
    val value = value
    val textAttributes = ExternalSystemJdkComboBox.getTextAttributes(value.valid, selected)

    icon(AllIcons.Nodes.PpJdk)

    text(value.label) {
      attributes = textAttributes
    }

    if (value.comment != null && value.comment != value.jdkName) {
      val commentAttributes: SimpleTextAttributes = when {
        !value.valid -> SimpleTextAttributes.ERROR_ATTRIBUTES
        SystemInfo.isMac && selected -> SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.WHITE)
        else -> SimpleTextAttributes.GRAY_ATTRIBUTES
      }

      text(value.comment) {
        attributes = commentAttributes
      }
    }
  }
}