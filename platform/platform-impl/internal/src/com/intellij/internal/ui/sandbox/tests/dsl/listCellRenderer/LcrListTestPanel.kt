// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization")

package com.intellij.internal.ui.sandbox.tests.dsl.listCellRenderer

import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.dsl.listCellRenderer.jbList
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import javax.swing.JComponent

internal class LcrListTestPanel : UISandboxPanel {

  override val title: String = "List"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        jbList(null, (1..99).toList(), listCellRenderer {
          text("Item $value")
          uiInspectorContext = listOf(PropertyBean("Item id", value), PropertyBean("Item text", "Item $value"))
        }).comment("List with uiInspectorContext")
      }
    }
  }
}
