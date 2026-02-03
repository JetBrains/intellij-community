// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.*
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.util.execution.ParametersListUtil
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrElse

internal class TestJavaMainClassCommand(
  private val mainClass: Class<*>,
  dependencies: List<Class<*>>,
  private val args: List<String>,
) {
  private val javaExe: Path = Path(ProcessHandle.current().info().command().getOrElse {
    // In rare cases on macOS, `ProcessHandle.current().info().command()` may be empty for unknown reasons.
    val failover = Path(System.getProperty("java.home")) / "bin" / (if (eelDescriptor.osFamily.isWindows) "java.exe" else "java")
    logger<TestJavaMainClassCommand>().warn("Cannot find java executable, using failover: $failover")
    failover.pathString
  })
  private val classpathEntries: List<Path> = getClasspathEntries(mainClass, dependencies)

  private val eelDescriptor: EelDescriptor
    get() = LocalEelDescriptor

  val commandLine: String
    get() = ParametersListUtil.join(listOf(javaExe.toString(), mainClass.canonicalName) + args)

  suspend fun createLocalProcessBuilder(): EelExecApiHelpers.SpawnProcess {
    val classpath = classpathEntries.joinToString(eelDescriptor.osFamily.pathSeparator) { it.pathString }
    return eelDescriptor.toEelApi().exec.spawnProcess(javaExe.pathString)
      .env(mapOf("CLASSPATH" to classpath))
      .args(listOf(mainClass.canonicalName) + args)
  }

  override fun toString(): String {
    return "TestJavaMainClassCommand(mainClass=$mainClass, args=$args)"
  }

  private companion object {
    private fun getClasspathEntries(mainClass: Class<*>, dependencies: List<Class<*>>): List<Path> {
      return (listOf(mainClass, KotlinVersion::class.java /* kotlin-stdlib.jar */) + dependencies).map {
        checkNotNull(PathManager.getJarForClass(it)) { "Cannot find jar/directory for $it" }
      }.distinct()
    }
  }
}
