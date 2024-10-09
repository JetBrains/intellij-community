// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extractModule

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectView.impl.ModuleNameValidator
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.SystemDependent
import javax.swing.JCheckBox
import javax.swing.JComponent

class ExtractModuleFromPackageDialog(private val project: Project, moduleName: String, private var sourceRootPath: @SystemDependent String) : DialogWrapper(project, true) {
  private val validator = ModuleNameValidator(project)
  internal var moduleName = moduleName
    private set
  private var moveToSeparateRoot = true
  private lateinit var panel: DialogPanel
  private lateinit var nameField: JBTextField
  private lateinit var pathField: TextFieldWithBrowseButton

  init {
    title = JavaUiBundle.message("dialog.title.extract.module.from.package")
    setOKButtonText(JavaUiBundle.message("button.text.extract.module"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    panel = panel {
      row(JavaUiBundle.message("dialog.message.module.name")) {
        nameField = textField()
          .bindText(::moduleName)
          .validationOnInput { validator.getErrorText(nameField.text)?.let { error(it) } }
          .align(AlignX.FILL)
          .component
      }
      lateinit var moveClassesCheckBox: JCheckBox
      row {
        moveClassesCheckBox = checkBox(JavaUiBundle.message("checkbox.move.classes.to.separate.source.root"))
          .bindSelected(::moveToSeparateRoot)
          .component
      }
      indent {
        row {
          pathField = textFieldWithBrowseButton(
            FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(JavaUiBundle.message("dialog.title.specify.path.to.new.source.root")),
            project
          )
            .bindText(::sourceRootPath)
            .enabledIf(moveClassesCheckBox.selected)
            .align(AlignX.FILL)
            .component
        }
      }
    }
    nameField.select(nameField.text.lastIndexOf('.') + 1, nameField.text.length)
    return panel
  }

  internal val targetSourceRootPath: String?
    get() = if (moveToSeparateRoot) FileUtil.toSystemIndependentName(sourceRootPath) else null
}
