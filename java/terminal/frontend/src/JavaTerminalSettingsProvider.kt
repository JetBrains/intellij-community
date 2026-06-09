// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.frontend

import com.intellij.java.terminal.shared.JavaTerminalBundle
import com.intellij.java.terminal.shared.JavaTerminalSettings
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.settings.TerminalSettingsProvider
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected

internal class JavaTerminalSettingsProvider : TerminalSettingsProvider {
  override fun createConfigurable(project: Project): UnnamedConfigurable = JavaTerminalConfigurable()

  private class JavaTerminalConfigurable: UiDslUnnamedConfigurable.Simple() {
    override fun Panel.createContent() {
      row {
        checkBox(JavaTerminalBundle.message("checkbox.override.jdk"))
          .bindSelected(JavaTerminalSettings.instance::overrideJavaHome)
      }
    }
  }
}