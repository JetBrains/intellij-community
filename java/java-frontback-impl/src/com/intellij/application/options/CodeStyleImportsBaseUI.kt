// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.java.frontback.impl.JavaFrontbackBundle
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.codeStyle.ImportsLayoutSettings
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTextField

open class CodeStyleImportsBaseUI(private val packages: JComponent, private val importLayout: JComponent) {

  private lateinit var cbUseSingleClassImports: JCheckBox
  private lateinit var cbUseFQClassNames: JCheckBox
  protected lateinit var cbInsertInnerClassImports: JCheckBox
  private lateinit var classCountField: JTextField
  private lateinit var namesCountField: JTextField

  lateinit var panel: DialogPanel

  // Use separate init method to allow use constructor params in fillCustomOptions
  open fun init() {
    panel = panel {
      group(ApplicationBundle.message("title.general")) {
        row {
          cbUseSingleClassImports = checkBox(JavaFrontbackBundle.message("checkbox.use.single.class.import")).component
        }
        row {
          cbUseFQClassNames = checkBox(JavaFrontbackBundle.message("checkbox.use.fully.qualified.class.names")).component
        }
        row {
          cbInsertInnerClassImports = checkBox(JavaFrontbackBundle.message("checkbox.insert.imports.for.inner.classes")).component
        }

        fillCustomOptions()

        row(JavaFrontbackBundle.message("editbox.class.count.to.use.import.with.star")) {
          classCountField = textField()
            .columns(3)
            .component
        }
        row(JavaFrontbackBundle.message("editbox.names.count.to.use.static.import.with.star")) {
          namesCountField = textField()
            .columns(3)
            .component
        }
      }.resizableRow()

      row {
        cell(packages).align(Align.FILL)
      }.resizableRow()
      row {
        cell(importLayout).align(Align.FILL)
      }.resizableRow()
    }.apply {
      border = JBUI.Borders.empty(0, 10, 10, 10)
    }
  }

  open fun Panel.fillCustomOptions() {}

  open fun reset(settings: ImportsLayoutSettings) {
    cbUseFQClassNames.setSelected(settings.isUseFqClassNames())
    cbUseSingleClassImports.setSelected(settings.isUseSingleClassImports())
    cbInsertInnerClassImports.setSelected(settings.isInsertInnerClassImports())
    classCountField.text = settings.getClassCountToUseImportOnDemand().toString()
    namesCountField.text = settings.getNamesCountToUseImportOnDemand().toString()
  }

  fun apply(settings: ImportsLayoutSettings) {
    settings.setUseFqClassNames(cbUseFQClassNames.isSelected)
    settings.setUseSingleClassImports(cbUseSingleClassImports.isSelected)
    settings.setInsertInnerClassImports(cbInsertInnerClassImports.isSelected)

    intValue(classCountField)?.let {
      settings.setClassCountToUseImportOnDemand(it)
    }

    intValue(namesCountField)?.let {
      settings.setNamesCountToUseImportOnDemand(it)
    }
  }

  fun isModified(settings: ImportsLayoutSettings): Boolean {
    return cbUseSingleClassImports.isSelected != settings.isUseSingleClassImports()
           || cbUseFQClassNames.isSelected != settings.isUseFqClassNames()
           || cbInsertInnerClassImports.isSelected != settings.isInsertInnerClassImports()
           || intValue(classCountField) != settings.getClassCountToUseImportOnDemand()
           || intValue(namesCountField) != settings.getNamesCountToUseImportOnDemand()
  }

  private fun intValue(textField: JTextField): Int? {
    return textField.getText().trim { it <= ' ' }.toIntOrNull()
  }
}
