// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.dsl.builder.*
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
      dropDownLink(JavaRefactoringBundle.message("migration.edit.copy.existing"), migrationMapSet.maps.mapSmart { it.name })
        .onChanged { dialog.copyMap(it.selectedItem) }
    }

    row(JavaRefactoringBundle.message("migration.map.description.label")) {
      descriptionTextArea = textArea()
        .rows(3)
        .applyToComponent {
          lineWrap = true
          wrapStyleWord = true
        }
        .align(AlignX.FILL)
        .component
      bottomGap(BottomGap.MEDIUM)
    }

    row {
      cell(tablePanel)
        .validationOnApply { dialog.validateTable() }
        .align(Align.FILL)
    }
  }
}