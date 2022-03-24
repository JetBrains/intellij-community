// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.containers.mapSmart
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField

class EditMigrationDialogUi(migrationMapSet: MigrationMapSet,
                            tablePanel: JPanel,
                            dialog: EditMigrationDialog) {
  lateinit var nameField: JTextField
  lateinit var descriptionTextArea: JTextArea

  val panel = panel {
    row(JavaRefactoringBundle.message("migration.map.name.prompt")) {
      nameField = textField()
        .columns(35)
        .validationOnApply { dialog.validateName(StringUtil.trim(nameField.text)) }
        .component
      dropDownLink(JavaRefactoringBundle.message("migration.edit.copy.existing"), migrationMapSet.maps.mapSmart { it.name },
                   onSelected = { dialog.copyMap(it) })
    }

    row(JavaRefactoringBundle.message("migration.map.description.label")) {
      descriptionTextArea = textArea()
        .rows(3)
        .applyToComponent {
          lineWrap = true
          wrapStyleWord = true
        }
        .horizontalAlign(HorizontalAlign.FILL)
        .component
      bottomGap(BottomGap.MEDIUM)
    }

    row {
      cell(tablePanel)
        .validationOnApply { dialog.validateTable() }
        .horizontalAlign(HorizontalAlign.FILL)
        .verticalAlign(VerticalAlign.FILL)
    }
  }
}