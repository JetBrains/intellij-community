// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.application.PathManager
import com.intellij.platform.eel.EelExecApiHelpers
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.spawnProcess
import com.intellij.util.execution.ParametersListUtil
import java.nio.file.Path
import kotlin.io.path.Path

internal class TestJavaMainClassCommand(
  private val mainClass: Class<*>,
  dependencies: List<Class<*>>,
  private val args: List<String>,
) {
  private val javaExe: Path = Path(ProcessHandle.current().info().command().get())
  private val classPath: String = getClassPath(mainClass, dependencies)

  val commandLine: String
    get() = ParametersListUtil.join(listOf(javaExe.toString(), mainClass.canonicalName) + args)

  fun createLocalProcessBuilder(): EelExecApiHelpers.SpawnProcess {
    return localEel.exec.spawnProcess(javaExe.toString())
      .env(mapOf("CLASSPATH" to classPath))
      .args(listOf(mainClass.canonicalName) + args)
  }

  override fun toString(): String {
    return "TestJavaMainClassCommand(mainClass=$mainClass, args=$args)"
  }

  private companion object {
    private fun getClassPath(mainClass: Class<*>, dependencies: List<Class<*>>): String {
      val classPathRoots = (listOf(mainClass, KotlinVersion::class.java /* kotlin-stdlib.jar */) + dependencies).map {
        checkNotNull(PathManager.getJarPathForClass(it)) { "Cannot find jar/directory for $it" }
      }.distinct()
      return classPathRoots.joinToString(LocalEelDescriptor.osFamily.pathSeparator)
    }
  }
}
