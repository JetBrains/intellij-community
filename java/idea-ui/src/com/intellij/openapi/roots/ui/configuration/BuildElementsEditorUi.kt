// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.ui.InsertPathAction
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox
import javax.swing.JRadioButton

class BuildElementsEditorUi(
  val compilerExtension: CompilerModuleExtension,
  val module: Module,
  fireModuleConfigurationChanged: Runnable,
  commitCompilerOutputPath: Runnable,
  commitTestCompilerOutputPath: Runnable,
) {

  lateinit var inheritCompilerOutput: JRadioButton
  lateinit var perModuleCompilerOutput: JRadioButton

  lateinit var compilerOutputPath: TextFieldWithBrowseButton
  lateinit var testCompilerOutputPath: TextFieldWithBrowseButton
  lateinit var excludeOutput: JCheckBox

  val panel = panel {
    group(JavaUiBundle.message("project.roots.output.compiler.title")) {
      buttonsGroup {
        row {
          inheritCompilerOutput = radioButton(JavaUiBundle.message("project.inherit.compile.output.path"))
            .onChanged { compilerExtension.inheritCompilerOutputPath(it.isSelected) }
            .component
        }
        row {
          perModuleCompilerOutput = radioButton(JavaUiBundle.message("project.module.compile.output.path"))
            .onChanged { compilerExtension.inheritCompilerOutputPath(!it.isSelected) }
            .component
        }
        indent {
          row(JavaUiBundle.message("module.paths.output.label")) {
            val descriptor = descriptor(JavaUiBundle.message("module.paths.output.title"))
            compilerOutputPath = textFieldWithBrowseButton(descriptor)
              .applyToComponent {
                InsertPathAction.addTo(textField, descriptor)
                FileChooserFactory.getInstance().installFileCompletion(textField, descriptor, true, null)
              }
              .align(AlignX.FILL)
              .onChanged { commitCompilerOutputPath.run() }
              .component
          }
          row(JavaUiBundle.message("module.paths.test.output.label")) {
            val descriptor = descriptor(JavaUiBundle.message("module.paths.test.output.title"))
            testCompilerOutputPath = textFieldWithBrowseButton(descriptor)
              .applyToComponent {
                InsertPathAction.addTo(textField, descriptor)
                FileChooserFactory.getInstance().installFileCompletion(textField, descriptor, true, null)
              }
              .align(AlignX.FILL)
              .onChanged { commitTestCompilerOutputPath.run() }
              .component
          }
          row {
            excludeOutput = checkBox(JavaUiBundle.message("module.paths.exclude.output.checkbox"))
              .onChanged {
                compilerExtension.isExcludeOutput = it.isSelected
                fireModuleConfigurationChanged.run()
              }
              .component
          }
        }
          .enabledIf(perModuleCompilerOutput.selected)
      }
    }
  }

  private fun descriptor(title: @DialogTitle String): FileChooserDescriptor {
    return FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(title)
      .withHideIgnored(false)
      .apply {
        putUserData(LangDataKeys.MODULE_CONTEXT, module)
      }
  }
}