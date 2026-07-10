// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.options

import com.intellij.compiler.impl.rmiCompiler.RmicConfiguration
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox

class RmicConfigurable(private val myProject: Project) :
  BoundSearchableConfigurable(JavaCompilerBundle.message("rmi.compiler.description"), "reference.projectsettings.compiler.rmicompiler"),
  NoScroll {

  private val settings = RmicConfiguration.getOptions(myProject)

  override fun createPanel(): DialogPanel {
    return panel {
      lateinit var enabledCb: JCheckBox

      row {
        enabledCb = checkBox(JavaCompilerBundle.message("rmic.option.enable.rmi.stubs"))
          .bindSelected(settings::IS_EANABLED)
          .component
      }

      rowsRange {
        row {
          checkBox(JavaCompilerBundle.message("rmic.option.generate.iiop.stubs"))
            .bindSelected(settings::GENERATE_IIOP_STUBS)
        }
        row {
          checkBox(JavaCompilerBundle.message("java.compiler.option.generate.debugging.info"))
            .bindSelected(settings::DEBUGGING_INFO)
        }
        row {
          checkBox(JavaCompilerBundle.message("java.compiler.option.generate.no.warnings"))
            .bindSelected(settings::GENERATE_NO_WARNINGS)
        }
        row(JavaCompilerBundle.message("java.compiler.option.additional.command.line.parameters")) {
          cell(RawCommandLineEditor())
            .bind({ it.text }, { it, text -> it.text = text }, settings::ADDITIONAL_OPTIONS_STRING.toMutableProperty())
            .align(AlignX.FILL)
        }
      }.enabledIf(enabledCb.selected)
    }
  }

  override fun apply() {
    try {
      super.apply()
    }
    finally {
      if (!myProject.isDefault) {
        BuildManager.getInstance().clearState(myProject)
      }
    }
  }
}
