// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.actions

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectView.impl.ModuleNameValidator
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import org.jetbrains.annotations.SystemDependent
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
        nameField = textField(::moduleName).withValidationOnInput {
          validator.getErrorText(nameField.text)?.let { error(it) }
        }.component
      }
      row {
        val moveClassesCheckBox = checkBox(JavaUiBundle.message("checkbox.move.classes.to.separate.source.root"), ::moveToSeparateRoot)
        row {
          pathField = textFieldWithBrowseButton(::sourceRootPath, JavaUiBundle.message("dialog.title.specify.path.to.new.source.root"), project,
                                                FileChooserDescriptorFactory.createSingleFolderDescriptor())
            .enableIf(moveClassesCheckBox.selected).component
        }
      }
    }
    nameField.select(nameField.text.lastIndexOf('.') + 1, nameField.text.length)
    return panel
  }

  internal val targetSourceRootPath: String?
    get() = if (moveToSeparateRoot) FileUtil.toSystemIndependentName(sourceRootPath) else null
}