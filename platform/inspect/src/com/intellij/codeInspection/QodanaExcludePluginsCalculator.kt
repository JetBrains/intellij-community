// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import java.io.File
import java.util.*

private val LOG = Logger.getInstance(QodanaExcludePluginsCalculator::class.java)

class QodanaExcludePluginsCalculator : ApplicationStarter {

  override fun getCommandName(): String {
    return "qodanaExcludedPlugins"
  }

  override fun getRequiredModality(): Int {
    return ApplicationStarter.NOT_IN_EDT
  }

  override fun main(args: MutableList<String>) {
    if (args.size < 3) {
      printHelpAndExit()
    }
    val input= args[1]
    val output = args[2]
    try {
      val included = File(input).readLines()

      val disabled = calculate(included)

      File(output).writeText(disabled.sorted().joinToString("\n") { it })

      ApplicationManagerEx.getApplicationEx().exit(true, true)
    }
    catch (e: Throwable) {
      LOG.error(e)
      System.exit(1)
    }
  }

  private fun printHelpAndExit() {
    println("Expected parameters: <path to file with list of included plugins> <output file>")
    System.exit(1)
  }

  private fun calculate(included: List<String>): List<String> {
    val plugins = PluginManagerCore.getLoadedPlugins()
    val pluginIds = plugins.map { it.pluginId.idString }
    val processed = mutableSetOf<String>()
    val toProcess = LinkedList<String>(included)
    val include = mutableSetOf<String>()


    while (toProcess.isNotEmpty()) {
      val idString = toProcess.pop()
      val pluginId = PluginId.findId(idString)
      if (pluginId == null) {
        LOG.error("Can't find plugin id '$idString'")
        continue
      }
      val descriptor = PluginManagerCore.getPlugin(pluginId)
      if (descriptor == null) {
        continue
      }
      toProcess += processPlugin(descriptor, include, processed)
    }

    println("=====INCLUDED=======")

    println(include.joinToString("\n") { it })

    val disabled = pluginIds - include

    println("=====DISABLED=======")

    println(disabled.sorted().joinToString("\n") { it })

    return disabled
  }

  private fun processPlugin(descriptor: IdeaPluginDescriptor, include: MutableSet<String>, processed: MutableSet<String>): List<String> {
    val idString = descriptor.pluginId.idString
    if (processed.contains(idString)) return emptyList()
    processed.add(idString)
    include.add(idString)

    return descriptor.dependencies.mapNotNull {
      if (processed.contains(it.pluginId.idString) || it.isOptional) null else it.pluginId.idString
    }
  }
}