// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.newclass

import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import javax.swing.ListCellRenderer

internal fun createTemplateListCellRenderer(): ListCellRenderer<CreateWithTemplatesDialogPanel.TemplatePresentation> {
  return listCellRenderer {
    value.icon?.let {
      icon(it)
    }
    text(value.kind)
  }
}