// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Internal
@file:Suppress("DialogTitleCapitalization")

package com.intellij.internal.ui.sandbox.dsl.listCellRenderer

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.FontComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.selectedValueIs
import com.intellij.util.text.nullize
import com.intellij.util.ui.FontInfo
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import javax.swing.JComboBox
import javax.swing.JComponent

internal class LcrOthersPanel : UISandboxPanel {

  private enum class RowHeightType {
    DEFAULT_FIXED,
    AUTO,
    CUSTOM,
  }

  override val title: String = "Others"

  override fun createContent(disposable: Disposable): JComponent {
    val result = panel {
      group("listCellRenderer") {
        row {
          lateinit var useSeparator: JBCheckBox
          lateinit var separatorText: JBTextField
          lateinit var rowHeight: JComboBox<RowHeightType>
          lateinit var rowHeightValue: JBIntSpinner
          lateinit var useCustomFont: JBCheckBox
          val customFont = FontComboBox()
          lateinit var icon: JBCheckBox
          lateinit var selected: JBCheckBox
          lateinit var placeholder: Placeholder
          val onChanged = { _: JComponent ->
            val rowHeightType = rowHeight.selectedItem as RowHeightType
            val customRowHeight = if (rowHeightType == RowHeightType.CUSTOM) rowHeightValue.number else null
            val customFontName = if (useCustomFont.isSelected) customFont.fontName else null
            val customFont = if (customFontName == null) null else FontInfo.get(customFontName).font
            createRenderer(placeholder, useSeparator.isSelected, separatorText.text, rowHeightType, customRowHeight,
                           icon.isSelected, selected.isSelected, customFont)
          }

          panel {
            row {
              useSeparator = checkBox("Separator")
                .gap(RightGap.SMALL)
                .onChanged(onChanged)
                .component
              separatorText = textField()
                .text("Group")
                .enabledIf(useSeparator.selected)
                .onChanged(onChanged)
                .component
            }
            row("Row height:") {
              rowHeight = comboBox(RowHeightType.entries)
                .gap(RightGap.SMALL)
                .onChanged(onChanged)
                .applyToComponent {
                  selectedItem = RowHeightType.DEFAULT_FIXED
                }
                .component
              rowHeightValue = spinner(1..100)
                .enabledIf(rowHeight.selectedValueIs(RowHeightType.CUSTOM))
                .applyToComponent {
                  number = 20
                  addChangeListener {
                    onChanged.invoke(this)
                  }
                }
                .component
            }
            row {
              useCustomFont = checkBox("Custom font:")
                .gap(RightGap.SMALL)
                .onChanged(onChanged)
                .component
              cell(customFont)
                .enabledIf(useCustomFont.selected)
                .onChanged(onChanged)
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
          onChanged.invoke(useSeparator)
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
    rowHeightType: RowHeightType, customRowHeight: Int?, icon: Boolean, selected: Boolean,
    font: Font?,
  ) {
    val debugColor = JBColor(0x80DD80, 0x70AA70)
    val renderer = listCellRenderer {
      when (rowHeightType) {
        RowHeightType.DEFAULT_FIXED -> {}
        RowHeightType.AUTO -> {
          rowHeight = null
        }
        RowHeightType.CUSTOM -> {
          rowHeight = customRowHeight
        }
      }

      if (separator) {
        separator {
          text = separatorText.nullize(true)
        }
      }

      if (icon) {
        icon(AllIcons.General.Gear)
      }

      text(value) {
        if (font != null) {
          this.font = font
        }
      }
      background = debugColor
    }

    placeholder.component = (renderer.getListCellRendererComponent(JBList(), "Some text", 1, selected, false) as JComponent).apply {
      isOpaque = true
      background = debugColor
    }
  }
}
