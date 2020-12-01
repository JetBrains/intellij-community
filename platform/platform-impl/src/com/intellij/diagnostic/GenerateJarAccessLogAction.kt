// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
package com.intellij.diagnostic

import com.intellij.ide.plugins.JarOrderStarter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.nio.file.Paths

internal class GenerateJarAccessLogAction : AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val path = Messages.showInputDialog(event.project, "Enter path to save log to:", "Jar access log", null) ?: return
    try {
      JarOrderStarter().generateJarAccessLog(Paths.get(path))
    }
    catch (e: Exception) {
      Messages.showMessageDialog(event.project, e.message, "Jar access log", null)
    }
  }
}