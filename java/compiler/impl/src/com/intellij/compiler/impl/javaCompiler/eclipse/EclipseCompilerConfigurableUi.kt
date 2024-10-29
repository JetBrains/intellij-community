// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.javaCompiler.eclipse

import com.intellij.compiler.impl.javaCompiler.CompilerModuleOptionsComponent
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox

class EclipseCompilerConfigurableUi(project: Project) {
  lateinit var debuggingInfoCb: JCheckBox
  lateinit var deprecationCb: JCheckBox
  lateinit var generateNoWarningsCb: JCheckBox
  lateinit var additionalOptionsField: RawCommandLineEditor
  lateinit var proceedOnErrorsCb: JCheckBox
  lateinit var optionsOverrideComponent: CompilerModuleOptionsComponent
  lateinit var pathToEcjField: TextFieldWithBrowseButton

  val panel: DialogPanel = panel {
    group(JavaCompilerBundle.message("eclipse.options.group.title")) {
      row {
        debuggingInfoCb = checkBox(JavaCompilerBundle.message("java.compiler.option.generate.debugging.info"))
          .component
      }
      row {
        deprecationCb = checkBox(JavaCompilerBundle.message("java.compiler.option.report.deprecated"))
          .component
      }
      row {
        generateNoWarningsCb = checkBox(JavaCompilerBundle.message("java.compiler.option.generate.no.warnings"))
          .component
      }
      row {
        proceedOnErrorsCb = checkBox(JavaCompilerBundle.message("eclipse.compiler.proceed.on.errors.option"))
          .component
      }
        .bottomGap(BottomGap.SMALL)
      row {
        val descriptor = FileChooserDescriptor(true, false, true, true, false, false)
          .withTitle(JavaCompilerBundle.message("path.to.ecj.compiler.tool"))
          .withExtensionFilter("jar")
        pathToEcjField = textFieldWithBrowseButton(descriptor, project)
          .align(AlignX.FILL)
          .label(JavaCompilerBundle.message("eclipse.compiler.path.label"), LabelPosition.TOP)
          .comment(JavaCompilerBundle.message("eclipse.compiler.path.comment"))
          .component
      }
      row {
        additionalOptionsField = cell(RawCommandLineEditor())
          .align(AlignX.FILL)
          .applyToComponent {
            setDescriptor(null, false)
          }
          .label(JavaCompilerBundle.message("java.compiler.option.additional.command.line.parameters"), LabelPosition.TOP)
          .comment(JavaCompilerBundle.message("settings.recommended.in.paths"))
          .component
      }
        .bottomGap(BottomGap.SMALL)
      row {
        optionsOverrideComponent = cell(CompilerModuleOptionsComponent(project))
          .align(AlignX.FILL)
          .resizableColumn()
          .component
      }
        .resizableRow()
    }
  }
}
