// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.memory.agent.MemoryAgentUtil
import com.intellij.java.JavaBundle
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

internal class DebuggerLaunchingConfigurable
  : BoundSearchableConfigurable(JavaBundle.message("options.java.display.name"), "reference.idesettings.debugger.launching") {

  override fun createPanel(): DialogPanel {
    val settings = DebuggerSettings.getInstance()
    return panel {
      buttonsGroup {
        row(JavaDebuggerBundle.message("label.debugger.launching.configurable.debugger.transport")) {
          radioButton(JavaDebuggerBundle.message("label.debugger.launching.configurable.socket"), DebuggerSettings.SOCKET_TRANSPORT)
          radioButton(JavaDebuggerBundle.message("label.debugger.launching.configurable.shmem"), DebuggerSettings.SHMEM_TRANSPORT)
            .enabled(SystemInfo.isWindows)
        }
      }.bind({ settings.transport }, { settings.transport = it })
      row {
        checkBox(JavaDebuggerBundle.message("label.debugger.general.configurable.show.alternative.source"))
          .bindSelected(settings::SHOW_ALTERNATIVE_SOURCE)
      }
      row {
        checkBox(JavaDebuggerBundle.message("label.debugger.general.configurable.kill.immediately"))
          .bindSelected(settings::KILL_PROCESS_IMMEDIATELY)
      }
      if (MemoryAgentUtil.isPlatformSupported()) {
        row {
          checkBox(JavaDebuggerBundle.message("label.debugger.general.configurable.enable.memory.agent"))
            .bindSelected(settings::ENABLE_MEMORY_AGENT)
            .applyToComponent { toolTipText = JavaDebuggerBundle.message("label.debugger.general.configurable.enable.memory.agent.tooltip.text") }
        }
      }
      if (Registry.`is`("execution.java.always.debug")) {
        row {
          checkBox(JavaDebuggerBundle.message("label.debugger.general.configurable.always.debug"))
            .bindSelected(settings::ALWAYS_DEBUG)
        }
      }
    }
  }
}
