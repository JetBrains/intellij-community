// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.options

import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import java.util.function.Consumer
import javax.swing.JCheckBox

class JavaCompilersTabUi(
  project: Project,
  val compilers: Collection<BackendCompiler>,
  configurableConsumer: Consumer<Configurable>,
  val compilerSelected: Consumer<BackendCompiler>,
) {

  lateinit var compilerComboBox: ComboBox<BackendCompiler>
  lateinit var useReleaseOptionCb: JCheckBox
  lateinit var targetOptionsComponent: TargetOptionsComponent

  val compilersMap = hashMapOf<String, Panel>()

  val panel = panel {
    row(JavaCompilerBundle.message("option.use.compiler.text")) {
      compilerComboBox = comboBox(compilers, textListCellRenderer { it?.presentableName })
        .applyToComponent {
          addActionListener {
            val compiler = compilerComboBox.getSelectedItem()
            if (compiler is BackendCompiler) compilerSelected.accept(compiler)
          }
        }
        .component
    }

    row {
      useReleaseOptionCb = checkBox(JavaCompilerBundle.message("settings.use.release.option.for.cross.compilation"))
        .component
    }

    row {
      targetOptionsComponent = cell(TargetOptionsComponent(project))
        .resizableColumn()
        .align(AlignX.FILL)
        .component
    }
      .bottomGap(BottomGap.MEDIUM)

    compilers.forEach { compiler ->
      val configurable = compiler.createConfigurable()
      configurableConsumer.accept(configurable)

      val component = configurable.createComponent()
      assert(component != null)
      val p = panel {
        row {
          cell(component!!)
            .resizableColumn()
            .align(AlignX.FILL)
        }
          .resizableRow()
      }

      compilersMap.put(compiler.id, p)
    }
  }

  fun show(id: String) {
    compilersMap.forEach { (compilerId, panel) ->
      panel.visible(id == compilerId)
    }
  }
}