// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Internal
@file:Suppress("DialogTitleCapitalization")

package com.intellij.internal.ui.sandbox.dsl.listCellRenderer

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.layout.selected
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBDimension
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

internal class LcrOthersPanel : UISandboxPanel {

  override val title: String = "Others"

  override fun createContent(disposable: Disposable): JComponent {
    val result = panel {
      group("listCellRenderer") {
        row {
          lateinit var separator: JBCheckBox
          lateinit var separatorText: JBTextField
          lateinit var fixedHeight: JBCheckBox
          lateinit var fixedHeightValue: JBIntSpinner
          lateinit var icon: JBCheckBox
          lateinit var selected: JBCheckBox
          lateinit var placeholder: Placeholder
          val onChanged = { _: JComponent ->
            val height = if (fixedHeight.isSelected) fixedHeightValue.number else null
            createRenderer(placeholder, separator.isSelected, separatorText.text, height, icon.isSelected, selected.isSelected)
          }

          panel {
            row {
              separator = checkBox("Separator")
                .gap(RightGap.SMALL)
                .onChanged(onChanged)
                .component
              separatorText = textField()
                .text("Group")
                .enabledIf(separator.selected)
                .onChanged(onChanged)
                .component
            }
            row {
              fixedHeight = checkBox("Fixed Height")
                .gap(RightGap.SMALL)
                .onChanged(onChanged)
                .selected(false)
                .component
              fixedHeightValue = spinner(1..100)
                .enabledIf(fixedHeight.selected)
                .applyToComponent {
                  number = 20
                  addChangeListener {
                    onChanged.invoke(this)
                  }
                }
                .component
            }
            row {
              icon = checkBox("Icon").selected(true)
                .onChanged(onChanged)
                .component
            }
            row {
              selected = checkBox("isSelected")
                .onChanged(onChanged)
                .component
            }
          }

          placeholder = placeholder()
            .align(AlignY.TOP)

          // Init
          onChanged.invoke(separator)
        }
      }

      group("Other") {
        row("SimpleListCellRenderer:") {
          val renderer = SimpleListCellRenderer.create<String> { label, value, _ ->
            label.icon = AllIcons.General.Gear
            label.text = value
          }
          cell(renderer.getListCellRendererComponent(JBList(), "Some text", 0, false, false) as JComponent)
        }
      }
    }

    result.registerValidators(disposable)
    return result
  }

  private fun createRenderer(
    placeholder: Placeholder, separator: Boolean, separatorText: String,
    fixedHeight: Int?, icon: Boolean, selected: Boolean,
  ) {
    val debugColor = JBColor(0x80DD80, 0x70AA70)
    val renderer = listCellRenderer {
      if (separator) {
        separator {
          text = separatorText.nullize(true)
        }
      }

      if (icon) {
        icon(AllIcons.General.Gear)
      }

      text(value)
      background = debugColor
    }

    placeholder.component = (renderer.getListCellRendererComponent(JBList(), "Some text", 1, selected, false) as JComponent).apply {
      isOpaque = true
      background = debugColor

      fixedHeight?.let {
        minimumSize = JBDimension(0, fixedHeight)
        preferredSize = JBDimension(preferredSize.width, fixedHeight)
      }
    }
  }
}
