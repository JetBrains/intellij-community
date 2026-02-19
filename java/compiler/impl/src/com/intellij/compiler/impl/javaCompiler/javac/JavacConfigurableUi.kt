// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.javaCompiler.javac

import com.intellij.compiler.impl.javaCompiler.CompilerModuleOptionsComponent
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.project.Project
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox

class JavacConfigurableUi(project: Project) {
  lateinit var preferTargetJdkCompilerCb: JBCheckBox
  lateinit var debuggingInfoCb: JCheckBox
  lateinit var deprecationCb: JCheckBox
  lateinit var generateNoWarningsCb: JCheckBox
  lateinit var additionalOptionsField: RawCommandLineEditor
  lateinit var optionsOverrideComponent: CompilerModuleOptionsComponent

  val panel = panel {
    group(JavaCompilerBundle.message("javac.options.group.title")) {
      row {
        preferTargetJdkCompilerCb = checkBox(JavaCompilerBundle.message("java.compiler.option.prefer.target.jdk.compiler"))
          .component
      }

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
        .bottomGap(BottomGap.SMALL)

      row {
        additionalOptionsField = cell(RawCommandLineEditor())
          .align(AlignX.FILL)
          .applyToComponent { setDescriptor(null, false) }
          .label(
            JavaCompilerBundle.message("java.compiler.option.additional.command.line.parameters"),
            LabelPosition.TOP
          )
          .comment(JavaCompilerBundle.message("settings.recommended.in.paths"))
          .component
      }
        .bottomGap(BottomGap.SMALL)

      row {
        optionsOverrideComponent = cell(CompilerModuleOptionsComponent(project))
          .align(AlignX.FILL)
          .component
      }
    }
  }

}













