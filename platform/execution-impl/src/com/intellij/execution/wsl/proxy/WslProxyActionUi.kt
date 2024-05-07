// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.proxy

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
@Suppress("HardCodedStringLiteral") // This is a test, internal only action
internal class WslProxyActionUi(project: Project, private val model: WslProxyActionModel) : DialogWrapper(project, false) {

  init {
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row {
      text("If you are using iperf3, on Windows run: iperf3 -s")
    }
    row("Local port") {
      spinner(IntRange(1, 65534)).bindIntValue(model::port)
    }
    row("Distro") {
      comboBox(model.wslDistributions).bindItem(model::selectedDistribution)
    }
  }
}
