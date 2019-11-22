// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.ide.plugins.JarOrderStarter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * @author yole
 */
class GenerateJarAccessLogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val path = Messages.showInputDialog(e.project, "Enter path to save log to:", "Jar access log", null) ?: return
    val classLoader = GenerateJarAccessLogAction::class.java.classLoader
    try {
      generateJarAccessLog(classLoader, path)
    }
    catch (ex: Exception) {
      Messages.showMessageDialog(e.project, ex.message, "Jar access log", null)
    }
  }
}

fun generateJarAccessLog(loader: ClassLoader, path: String) {
  JarOrderStarter().generateJarAccessLog(loader, path)
}
