// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class JavaHotSwapConfigurableUi : ConfigurableUi<DebuggerSettings> {

  private val settings = DebuggerSettings.getInstance()
  private lateinit var panel: DialogPanel

  override fun reset(settings: DebuggerSettings) {
    panel.reset()
  }

  override fun isModified(settings: DebuggerSettings): Boolean {
    return panel.isModified()
  }

  override fun apply(settings: DebuggerSettings) {
    panel.apply()
  }

  override fun getComponent(): JComponent {
    panel = panel {
      row {
        checkBox(JavaDebuggerBundle.message("label.debugger.hotswap.configurable.compile.before.hotswap"))
          .bindSelected(settings::COMPILE_BEFORE_HOTSWAP)
      }
      row {
        checkBox(JavaDebuggerBundle.message("label.debugger.hotswap.configurable.enable.vm.hang.warning"))
          .bindSelected(settings::HOTSWAP_HANG_WARNING_ENABLED)
      }
      row {
        checkBox(JavaDebuggerBundle.message("label.debugger.hotswap.configurable.suggest.in.editor"))
          .bindSelected(settings::HOTSWAP_SHOW_FLOATING_BUTTON)
      }

      buttonsGroup {
        row(JavaDebuggerBundle.message("label.debugger.hotswap.configurable.reload.classes")) {
          radioButton(JavaDebuggerBundle.message("label.debugger.hotswap.configurable.always"),
                      DebuggerSettings.RUN_HOTSWAP_ALWAYS)
          radioButton(JavaDebuggerBundle.message("label.debugger.hotswap.configurable.never"),
                      DebuggerSettings.RUN_HOTSWAP_NEVER)
          radioButton(JavaDebuggerBundle.message("label.debugger.hotswap.configurable.ask"),
                      DebuggerSettings.RUN_HOTSWAP_ASK)
        }
      }.bind(settings::RUN_HOTSWAP_AFTER_COMPILE)
    }

    return panel
  }
}
