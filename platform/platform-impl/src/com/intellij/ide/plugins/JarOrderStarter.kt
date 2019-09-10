// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationStarter
import java.io.File
import kotlin.reflect.full.memberFunctions
import kotlin.streams.toList
import kotlin.system.exitProcess

/**
 * This class is used in build scripts for providing module/jar loading order. 
 * Unfortunately we cannot start this code on the final jar-s so the output contains mostly module names (jar only for libraries):
 * intellij.platform.bootstrap 
 * intellij.platform.impl
 * log4j-1.4.5.jar
 * trove-4.4.4.jar
 * intellij.platform.api
 * ...
 * Afterward the build scripts convert the output file to the final list of jar files (classpath-order.txt)
 * 
 */
class JarOrderStarter : ApplicationStarter {
  override fun getCommandName(): String = "jarOrder"

  override fun main(args: Array<String>) = generateJarAccessLog(JarOrderStarter::class.java.classLoader, args[1])

  fun generateJarAccessLog(loader: ClassLoader, path: String?) {
    // Must use reflection because the classloader class is loaded with a different classloader
    val accessor = loader::class.memberFunctions.find { it.name == "getJarAccessLog" }
                   ?: throw Exception("Can't find getJarAccessLog() method")
    @Suppress("UNCHECKED_CAST") val log = accessor.call(loader) as? Collection<String>
                                          ?: throw Exception("Unexpected return type of getJarAccessLog()")
    val result = log
      .stream()
      .map { it.removePrefix("jar:").removePrefix("/").removeSuffix("!/").removeSuffix("/") }
      .map {
        val last = it.lastIndexOf('/')
        if (last >= 0) it.substring(last) else it
      }
      .limit(25)
      .toList()
      .joinToString("\n") { it.removePrefix("/") }
    if (result.isNotEmpty()) {
      File(path).writeText(result)
    }

    exitProcess(0)
  }

}
