// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.ui.Messages
import java.io.File
import kotlin.reflect.full.memberFunctions

/**
 * @author yole
 */
class ShowJarAccessLogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val classLoader = ShowJarAccessLogAction::class.java.classLoader
    val log = generateJarAccessLog(classLoader)
    if (log != null) {
      Messages.showMessageDialog(e.project, log, "Jar access log", null)
    }
    else {
      Messages.showMessageDialog(e.project, "Unexpected classloader: $classLoader", "Jar access log", null)
    }
  }
}

fun generateJarAccessLog(loader: ClassLoader): String? {
  // Must use reflection because the classloader class is loaded with a different classloader
  val accessor = loader::class.memberFunctions.find { it.name == "getJarAccessLog" } ?: return null
  @Suppress("UNCHECKED_CAST") val log = accessor.call(loader) as? Collection<String> ?: return null
  val prefix = File(PathManager.getLibPath()).toURI().toURL().toString()
  val result = log
    .map { it.removePrefix("jar:") }
    .filter { it.startsWith(prefix) }
    .joinToString("\n") { it.removePrefix(prefix).removePrefix("/").removeSuffix(":/") }
  if (result.isEmpty()) return prefix
  return result
}
