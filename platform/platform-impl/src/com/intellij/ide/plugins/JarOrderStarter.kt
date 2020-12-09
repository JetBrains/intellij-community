// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.idea.IdeStarter
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.util.lang.ClassPath
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
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
internal class JarOrderStarter : IdeStarter() {
  override fun getCommandName() = "jarOrder"

  override fun isHeadless() = true

  override fun main(args: List<String>) {
    try {
      super.main(args)
      IconLoader::class.java
      WindowManagerImpl::class.java
      ActionManager.getInstance()
      // ensure that all EDT activities were processed
      ApplicationManager.getApplication().invokeLater {
        // and activities that were scheduled as part of invoke too
        ApplicationManager.getApplication().invokeLater {
          generateJarAccessLog(Paths.get(args[1]))
          exitProcess(0)
        }
      }
    }
    catch (e: Throwable) {
      logger<JarOrderStarter>().error(e)
      exitProcess(1)
    }
  }

  fun generateJarAccessLog(outFile: Path) {
    val classLoader = JarOrderStarter::class.java.classLoader
    val itemsFromBootstrap = MethodHandles.lookup()
      .findStatic(classLoader::class.java, "getLoadedClasses", MethodType.methodType(Collection::class.java))
      .invokeExact() as Collection<Map.Entry<String, Path>>
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
