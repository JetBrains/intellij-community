// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.migration

import com.intellij.application.options.ModulesComboBox
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel

class MigrationDialogUi(map: MigrationMap?) {

  val predefined = if (map == null) false else MigrationMapSet.isPredefined(map.fileName)
  lateinit var nameLabel: JLabel
  var removeLink: ActionLink? = null
  lateinit var editLink: ActionLink
  lateinit var descriptionLabel: JEditorPane
  lateinit var modulesCombo: ModulesComboBox

  val panel = panel {
    row {
      nameLabel = label(map?.name ?: "")
        .resizableColumn()
        .component
      if (!predefined) {
        removeLink = link(JavaRefactoringBundle.message("migration.dialog.link.delete")) {}
          .component
      }
      editLink = link(JavaRefactoringBundle.message(if (predefined) "migration.dialog.link.duplicate" else "migration.dialog.link.edit")) {}
        .component
    }

    row(JavaRefactoringBundle.message("migration.dialog.scope.label")) {
      modulesCombo = cell(ModulesComboBox())
        .applyToComponent { setMinimumAndPreferredWidth(JBUI.scale(380)) }
        .horizontalAlign(HorizontalAlign.FILL)
        .component
      bottomGap(BottomGap.SMALL)
    }

    row {
      descriptionLabel = text(map?.description ?: "", DEFAULT_COMMENT_WIDTH)
        .component
    }
  }

  fun preferredFocusedComponent(): JComponent = editLink

  fun update(map: MigrationMap) {
    nameLabel.text = map.name
    descriptionLabel.text = map.description
  }

}