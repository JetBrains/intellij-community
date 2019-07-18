// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.ide.BootstrapClassLoaderUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
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

class GenerateJarAccessLogActivity : StartupActivity {
  override fun runActivity(project: Project) {
    if (!Registry.`is`("ide.reorder.jars.in.classpath")) {
      return
    }
    
    val orderFile = File(PathManager.getSystemPath(), BootstrapClassLoaderUtil.CLASSPATH_ORDER_FILE)
    if (!orderFile.exists()) {
      try {
        generateJarAccessLog(GenerateJarAccessLogActivity::class.java.classLoader, orderFile.path)
      }
      catch (e: Exception) {
        // ignore
      }
    }
  }
}

fun generateJarAccessLog(loader: ClassLoader, path: String) {
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
