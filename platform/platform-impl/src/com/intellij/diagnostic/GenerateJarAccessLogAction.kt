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
  if (System.getProperty("idea.log.jar.access") == null) {
    throw Exception("Jar access log not recorded")
  }
  // Must use reflection because the classloader class is loaded with a different classloader
  val accessor = loader::class.memberFunctions.find { it.name == "getJarAccessLog" }
                 ?: throw Exception("Can't find getJarAccessLog() method")
  @Suppress("UNCHECKED_CAST") val log = accessor.call(loader) as? Collection<String>
                                        ?: throw Exception("Unexpected return type of getJarAccessLog()")
  val prefix = File(PathManager.getLibPath()).toURI().toURL().toString()
  val result = log
    .map { it.removePrefix("jar:") }
    .filter { it.startsWith(prefix) }
    .joinToString("\n") { it.removePrefix(prefix).removePrefix("/").removeSuffix("!/") }
  if (result.isEmpty()) throw Exception("Unexpected URL format in jar access log: ${log.joinToString()}")
  File(path).writeText(result)
}
