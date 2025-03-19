// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.ComboBox
import com.intellij.psi.codeStyle.CodeStyleConstraints
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.fields.CommaSeparatedIntegersField
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer

internal class GeneralCodeStyleGeneralTab(settings: CodeStyleSettings) {

  private lateinit var myLineSeparatorCombo: ComboBox<LineSeparator>

  @JvmField
  val myRightMarginField = IntegerField(ApplicationBundle.message("editbox.right.margin.columns"), 0,
                                        CodeStyleConstraints.MAX_RIGHT_MARGIN)
    .apply {
      defaultValue = settings.defaultRightMargin
    }
  lateinit var myCbWrapWhenTypingReachesRightMargin: JBCheckBox

  @JvmField
  val myVisualGuides = CommaSeparatedIntegersField(ApplicationBundle.message("settings.code.style.visual.guides"),
                                                   0, CodeStyleConstraints.MAX_RIGHT_MARGIN,
                                                   ApplicationBundle.message("settings.code.style.visual.guides.optional"))
  lateinit var myAutodetectIndentsBox: JBCheckBox
  private lateinit var generalOptions: Placeholder

  var lineSeparator: String?
    get() = myLineSeparatorCombo.item.value
    set(value) {
      val lineSeparator = LineSeparator.values().find { it.value == value } ?: LineSeparator.SystemDependant
      myLineSeparatorCombo.item = lineSeparator
    }

  @JvmField
  val panel = panel {
    row(ApplicationBundle.message("combobox.line.separator.for.new.files")) {
      myLineSeparatorCombo = comboBox(EnumComboBoxModel(LineSeparator::class.java), textListCellRenderer { it?.text })
        .comment(ApplicationBundle.message("combobox.lineseparator.for.new.files.hint"))
        .component
    }
    row(ApplicationBundle.message("editbox.right.margin.columns")) {
      cell(myRightMarginField)
        .columns(COLUMNS_SHORT)
        .gap(RightGap.SMALL)
      label(ApplicationBundle.message("margin.columns"))
      myCbWrapWhenTypingReachesRightMargin = checkBox(ApplicationBundle.message("wrapping.wrap.on.typing")).component
    }
    row(ApplicationBundle.message("settings.code.style.visual.guides") + ":") {
      cell(myVisualGuides)
        .comment(ApplicationBundle.message("settings.code.style.general.visual.guides.hint"))
        .columns(COLUMNS_SHORT)
        .gap(RightGap.SMALL)
      label(ApplicationBundle.message("margin.columns"))
    }.bottomGap(BottomGap.SMALL)
    row {
      myAutodetectIndentsBox = checkBox(ApplicationBundle.message("settings.code.style.general.autodetect.indents")).component
    }
    row {
      generalOptions = placeholder().align(AlignX.FILL)
    }
  }

  fun updateGeneralOptions(options: List<GeneralCodeStyleOptionsProvider>) {
    val placeholderPanel = panel {
      for (provider in options) {
        val component = provider.createComponent()
        if (component != null) {
          row {
            cell(component).align(AlignX.FILL)
          }
        }
      }
    }
    generalOptions.component = placeholderPanel
  }
}

private enum class LineSeparator(val text: String, val value: String?) {
  SystemDependant(ApplicationBundle.message("combobox.crlf.system.dependent"), null),
  Unix(ApplicationBundle.message("combobox.crlf.unix"), "\n"),
  Windows(ApplicationBundle.message("combobox.crlf.windows"), "\r\n"),
  Mac(ApplicationBundle.message("combobox.crlf.mac"), "\r")
}