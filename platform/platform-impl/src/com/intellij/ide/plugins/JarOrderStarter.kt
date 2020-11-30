// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.write
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.system.exitProcess

/**
 * This class is used in build scripts for providing classes and modules loading orders.
 *
 * Performance tests show 200ms startup performance improvement after adding jar files order
 * Performance tests (mostly Windows) show 600ms startup performance improvement after reordering classes in jar files
 */
@Suppress("UNCHECKED_CAST")
internal class JarOrderStarter : ApplicationStarter {
  override fun getCommandName() = "jarOrder"

  override fun getRequiredModality() = ApplicationStarter.NOT_IN_EDT

  override fun main(args: List<String>) {
    try {
      generateJarAccessLog(JarOrderStarter::class.java.classLoader, Paths.get(args[1]))
      generateClassesAccessLog(JarOrderStarter::class.java.classLoader, Paths.get(args[2]))
    }
    catch (e: Throwable) {
      logger<JarOrderStarter>().error(e)
    }
    finally {
      exitProcess(0)
    }
  }

  /**
   * Generates module loading order file.
   *
   * We cannot start this code on the final jar-s so the output contains mostly module paths (jar only for libraries):
   * path/to/intellij.platform.bootstrap
   * path/to/intellij.platform.impl
   * path/to/log4j-1.4.5.jar
   * path/to/intellij.platform.api
   * ...
   * Afterward the build scripts convert the output file to the final list of jar files (classpath-order.txt)
   */
  fun generateJarAccessLog(loader: ClassLoader, path: Path) {
    // must use reflection because the classloader class is loaded with a different classloader
    val accessor = loader::class.memberFunctions.find { it.name == "getJarAccessLog" }
                   ?: throw Exception("Can't find getJarAccessLog() method")
    val log = accessor.call(loader) as? Collection<String> ?: throw Exception("Unexpected return type of getJarAccessLog()")
    val result = log.joinToString("\n") { it.removePrefix("jar:").removePrefix("file:").removeSuffix("!/").removeSuffix("/") }
    if (result.isNotEmpty()) {
      path.write(result)
    }
  }

  /**
   * Generates class loading order file.
   *
   * Format:
   * com/package/ClassName.class:path/to/intellij.platform.impl
   * com/package2/ClassName2.class:path/to/module
   * com/package3/ClassName3.class:path/to/some.jar
   */
  private fun generateClassesAccessLog(loader: ClassLoader, path: Path) {
    val classPathMember = loader::class.memberFunctions.find { it.name == "getClassPath" }
    classPathMember?.isAccessible = true
    val result = classPathMember?.call(loader) ?: return
    val javaClass = result.javaClass
    val field = javaClass.getDeclaredField("ourLoadedClasses")
    field.isAccessible = true

    val log = field.get(null) as? Collection<String> ?: throw Exception("Unexpected type of ourLoadedClasses")
    if (log.isNotEmpty()) {
      synchronized(log) {
        path.write(log.joinToString(separator = "\n"))
      }
    }
  }
}
