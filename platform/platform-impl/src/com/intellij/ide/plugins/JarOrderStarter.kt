// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.lang.ClassPath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
      generateJarAccessLog(Paths.get(args[1]))
    }
    catch (e: Throwable) {
      logger<JarOrderStarter>().error(e)
      exitProcess(1)
    }
    finally {
      exitProcess(0)
    }
  }

  fun generateJarAccessLog(outFile: Path) {
    val classLoader = JarOrderStarter::class.java.classLoader
    val getClassPath = classLoader::class.java.getDeclaredMethod("getClassPath")
    getClassPath.isAccessible = true
    val getLoadedClasses = getClassPath.invoke(classLoader)::class.java.getDeclaredMethod("getLoadedClasses")
    getLoadedClasses.isAccessible = true
    val itemsFromBootstrap = getLoadedClasses.invoke(null) as Collection<Map.Entry<String, Path>>
    val itemsFromCore = ClassPath.getLoadedClasses()
    val items = LinkedHashSet<Map.Entry<String, Path>>(itemsFromBootstrap.size + itemsFromCore.size)
    items.addAll(itemsFromBootstrap)
    items.addAll(itemsFromCore)

    val builder = StringBuilder()
    for (item in items) {
      builder.append(item.key).append(':').append(item.value)
      builder.append('\n')
    }
    Files.writeString(outFile, builder)
  }
}
