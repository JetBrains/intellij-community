// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.InsertPathAction
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import java.util.function.Consumer
import javax.swing.JCheckBox
import javax.swing.JRadioButton
import javax.swing.event.DocumentEvent

class BuildElementsEditorUi(
  val module: Module,
  enableCompilerSettings: Consumer<Boolean>,
  commitCompilerOutputPath: Runnable,
  commitTestCompilerOutputPath: Runnable,
  commitExcludeOutputPaths: Consumer<Boolean>,
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
            .onChanged { enableCompilerSettings.accept(!it.isSelected) }
            .component
        }
        row {
          perModuleCompilerOutput = radioButton(JavaUiBundle.message("project.module.compile.output.path"))
            .onChanged { enableCompilerSettings.accept(it.isSelected) }
            .component
        }
        indent {
          row(JavaUiBundle.message("module.paths.output.label")) {
            val descriptor = descriptor(JavaUiBundle.message("module.paths.output.title"))
            compilerOutputPath = textFieldWithBrowseButton(descriptor)
              .applyToComponent {
                InsertPathAction.addTo(textField, descriptor)
                FileChooserFactory.getInstance().installFileCompletion(textField, descriptor, true, null)
                addTextFieldListeners(commitCompilerOutputPath)
              }
              .align(AlignX.FILL)
              .component
          }
          row(JavaUiBundle.message("module.paths.test.output.label")) {
            val descriptor = descriptor(JavaUiBundle.message("module.paths.test.output.title"))
            testCompilerOutputPath = textFieldWithBrowseButton(descriptor)
              .applyToComponent {
                InsertPathAction.addTo(textField, descriptor)
                FileChooserFactory.getInstance().installFileCompletion(textField, descriptor, true, null)
                addTextFieldListeners(commitTestCompilerOutputPath)
              }
              .align(AlignX.FILL)
              .component
          }
          row {
            excludeOutput = checkBox(JavaUiBundle.message("module.paths.exclude.output.checkbox"))
              .actionListener { _, checkBox -> commitExcludeOutputPaths.accept(checkBox.isSelected) }
              .component
          }
        }
          .enabledIf(perModuleCompilerOutput.selected)
      }
    }
  }

  private fun TextFieldWithBrowseButton.addTextFieldListeners(commitRunnable: Runnable) {
    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        if (perModuleCompilerOutput.isSelected)
          commitRunnable.run()
      }
    })
    addActionListener { e ->
      if (perModuleCompilerOutput.isSelected) {
        commitRunnable.run()
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