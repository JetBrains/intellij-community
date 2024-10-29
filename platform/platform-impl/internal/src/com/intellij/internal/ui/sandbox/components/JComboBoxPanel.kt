// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.withStateLabel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.border.TitledBorder
import kotlin.math.max

internal class JComboBoxPanel : UISandboxPanel {

  override val title: String = "JComboBox"

  override fun createContent(disposable: Disposable): JComponent {
    val items = (1..10).map { "Item $it" }.toList()

    val result = panel {
      withStateLabel {
        comboBox(items)
      }
      withStateLabel {
        comboBox(items).enabled(false)
      }
      withStateLabel {
        comboBox(items).applyToComponent {
          isEditable = true
        }
      }
      withStateLabel {
        comboBox(items)
          .enabled(false)
          .applyToComponent {
            isEditable = true
          }
      }

      group("IS_EMBEDDED_PROPERTY") {
        withStateLabel {
          comboBox(items).applyToComponent {
            putClientProperty(ComboBox.IS_EMBEDDED_PROPERTY, true)
          }
        }
        withStateLabel {
          comboBox(items)
            .enabled(false)
            .applyToComponent {
              putClientProperty(ComboBox.IS_EMBEDDED_PROPERTY, true)
            }
        }
      }

      group("IS_BORDERLESS_PROPERTY") {
        withStateLabel {
          comboBox(items).applyToComponent {
            putClientProperty(ComboBox.IS_BORDERLESS_PROPERTY, true)
          }
        }
        withStateLabel {
          comboBox(items)
            .enabled(false)
            .applyToComponent {
              putClientProperty(ComboBox.IS_BORDERLESS_PROPERTY, true)
            }
        }
      }

      group("IS_TABLE_CELL_EDITOR_PROPERTY") {
        withStateLabel {
          comboBox(items).applyToComponent {
            putClientProperty(ComboBox.IS_TABLE_CELL_EDITOR_PROPERTY, true)
          }
        }
        withStateLabel {
          comboBox(items)
            .enabled(false)
            .applyToComponent {
              putClientProperty(ComboBox.IS_TABLE_CELL_EDITOR_PROPERTY, true)
            }
        }
      }

      group("Validation") {
        withStateLabel("Error") {
          comboBox(items).validationOnInput {
            validate(it, true)
          }.validationOnApply {
            validate(it, true)
          }
        }

        withStateLabel("Warning") {
          comboBox(items).validationOnInput {
            validate(it, false)
          }.validationOnApply {
            validate(it, false)
          }
        }
      }

      row("Custom border:") {
        val comboBox = comboBox(listOf("Item 1", "Item 2")).component
        setTitledBorder(comboBox)
      }
    }

    result.registerValidators(disposable)
    result.validateAll()

    return result
  }

  private fun setTitledBorder(comboBox: ComboBox<*>) {
    val titledBorder = object : TitledBorder(
      comboBox.border,
      "Title",
      DEFAULT_JUSTIFICATION,
      TOP,
    ) {
      override fun getTitleColor(): Color {
        return if (comboBox.hasFocus()) JBUI.CurrentTheme.Focus.focusColor() else DarculaUIUtil.getOutlineColor(true, false)
      }

      override fun getTitleFont(): Font {
        val size = max(JBFont.labelFontSize() - JBUIScale.scale(6f), JBUIScale.scale(8f))
        return JBUI.Fonts.label().deriveFont(size)
      }

      override fun getBaseline(c: Component?, width: Int, height: Int): Int {
        return -1
      }

      override fun getBorderInsets(c: Component): Insets {
        return getBorder().getBorderInsets(c)
      }
    }
    comboBox.border = titledBorder
    comboBox.background = JBUI.CurrentTheme.CustomFrameDecorations.paneBackground()
    (comboBox.ui as DarculaComboBoxUI).isPaintArrowButton = false
  }

  private fun validate(comboBox: ComboBox<*>, isError: Boolean): ValidationInfo? {
    if (comboBox.selectedItem == "Item 2") {
      return null
    }

    return if (isError) {
      ValidationInfo("Item 2 must be selected")
    }
    else {
      ValidationInfo("Item 2 should be selected").asWarning()
    }
  }
}