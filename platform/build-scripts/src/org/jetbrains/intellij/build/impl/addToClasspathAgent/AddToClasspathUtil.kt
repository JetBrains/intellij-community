// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.addToClasspathAgent

import com.intellij.openapi.util.SystemInfo
import org.jetbrains.intellij.build.io.runJava
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.absolutePathString

object AddToClasspathUtil {
  private var addStackTrace: Throwable? = null

  suspend fun addToClassPathViaAgent(additionalClassPath: List<Path>) {
    require(additionalClassPath.isNotEmpty())

    if (addStackTrace != null) {
      throw IllegalStateException("this method could be called only once, see nested exception for previous call", addStackTrace)
    }
    else {
      addStackTrace = Throwable()
    }

    val mainClass = AddToClasspathJavaAgent::class.java

    val agentPath = createAgentJar(mainClass)
    attachAgent(agentPath, mainClass, additionalClassPath.joinToString(File.pathSeparator))
  }

  private suspend fun attachAgent(agentPath: Path, mainClass: Class<*>, agentArguments: String) {
    // -Djdk.attach.allowAttachSelf required to attach to self,
    // we could not guarantee any additional system parameters upon start
    // since code will be run as any jvm main class e.g. from IDE gutter mark
    // so attach from another process

    val absoluteAgentPath = agentPath.absolutePathString()

    val javaFileName = if (SystemInfo.isWindows) "java.exe" else "java"
    val javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaFileName)
    check(Files.exists(javaExecutable)) {
      "Java executable is not found at $javaExecutable"
    }

    runJava(
      mainClass = mainClass.name,
      args = listOf("attach-agent", ProcessHandle.current().pid().toString(), absoluteAgentPath, agentArguments),
      jvmArgs = emptyList(),
      classPath = listOf(absoluteAgentPath),
      javaExe = javaExecutable,
    )
  }

  @Suppress("SSBasedInspection")
  private fun createAgentJar(agentClass: Class<*>): Path {
    val jar = File.createTempFile("add-to-classpath-agent", ".jar")
    jar.deleteOnExit()

    val manifest = Manifest().apply {
      mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
      mainAttributes[Attributes.Name.MAIN_CLASS] = agentClass.name
      mainAttributes.putValue("Agent-Class", agentClass.name)
    }

    jar.outputStream().buffered().let { JarOutputStream(it, manifest) }.use { jarStream ->
      for (klass in listOf(agentClass)) {
        val classEntryName = klass.name.replace(".", "/") + ".class"

        jarStream.putNextEntry(JarEntry(classEntryName))
        klass.classLoader.getResourceAsStream(classEntryName)!!.use {
          it.copyTo(jarStream)
        }
        jarStream.closeEntry()
      }
    }

    return jar.toPath()
  }
}