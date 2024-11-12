// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.internal.ui.sandbox.dsl.listCellRenderer

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.ApiStatus
import java.util.*
import javax.swing.*

internal class LcrComboBoxPanel : UISandboxPanel {

  override val title: String = "ComboBox"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      lateinit var enabled: JCheckBox
      row {
        enabled = checkBox("Enabled")
          .selected(true)
          .component
      }
      group("ComboBox") {
        row("Empty:") {
          comboBox(emptyList<String>(), textListCellRenderer { it })
        }
        row("No selection:") {
          comboBox(listOf("First", "Second", "Last"), textListCellRenderer { it })
            .applyToComponent { selectedItem = null }
        }
        row("Few items:") {
          comboBox(listOf("First", "Second", "Try with y", "Try with ()"), textListCellRenderer { it })
        }
        row("Items with icon:") {
          comboBox((1..100).toList(), listCellRenderer {
            value?.let {
              icon(if (it % 2 == 0) AllIcons.General.Information else AllIcons.General.Gear)
              text("Item $it")
            }
          })
        }
        row("Long items:") {
          comboBox((1..100).map { "$it " + "Item".repeat(10) }, textListCellRenderer { it }).component
        }
      }.enabledIf(enabled.selected)
      group("JComboBox") {
        row("Empty:") {
          jComboBox(emptyList<String>(), textListCellRenderer { it })
        }
        row("No selection:") {
          jComboBox(listOf("First", "Second", "Last"), textListCellRenderer { it })
            .applyToComponent { selectedItem = null }
        }
        row("Few items:") {
          jComboBox(listOf("First", "Second", "Try with y", "Try with ()"), textListCellRenderer { it })
        }
        row("Items with icon:") {
          jComboBox((1..100).toList(), listCellRenderer {
            value?.let {
              icon(if (it % 2 == 0) AllIcons.General.Information else AllIcons.General.Gear)
              text("Item $it")
            }
          })
        }
        row("Long items:") {
          jComboBox((1..100).map { "$it " + "Item".repeat(10) }, textListCellRenderer { it }).component
        }
      }.enabledIf(enabled.selected)
    }
  }

  private fun <T> Row.jComboBox(items: Collection<T>, renderer: ListCellRenderer<in T?>? = null): Cell<JComboBox<T>> {
    val model = DefaultComboBoxModel<T>(Vector(items))
    return cell(JComboBox(model)).applyToComponent {
      setRenderer(renderer)
    }
  }
}
