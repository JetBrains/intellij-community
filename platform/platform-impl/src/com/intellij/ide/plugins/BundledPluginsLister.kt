// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.util.io.jackson.array
import com.intellij.util.io.jackson.obj
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

internal class BundledPluginsLister : ApplicationStarter {
  override fun getCommandName() = "listBundledPlugins"

  override fun getRequiredModality() = ApplicationStarter.NOT_IN_EDT

  // not premain because FileTypeManager is used to report extensions
  override fun main(args: List<String>) {
    try {
      val out: Writer = if (args.size == 2) {
        val outFile = Path.of(args[1])
        Files.createDirectories(outFile.parent)
        Files.newBufferedWriter(outFile)
      }
      else {
        // noinspection UseOfSystemOutOrSystemErr
        OutputStreamWriter(System.out, StandardCharsets.UTF_8)
      }
      JsonFactory().createGenerator(out).useDefaultPrettyPrinter().use { writer ->
        val plugins = PluginManagerCore.getPluginSet().enabledPlugins
        val modules = ArrayList<String>()
        val pluginIds = ArrayList<String>(plugins.size)
        for (plugin in plugins) {
          pluginIds.add(plugin.pluginId.idString)
          for (pluginId in plugin.modules) {
            modules.add(pluginId.idString)
          }
        }
        pluginIds.sort()
        modules.sort()
        val fileTypeManager = FileTypeManager.getInstance()
        val extensions = ArrayList<String>()
        for (type in fileTypeManager.registeredFileTypes) {
          if (type !is PlainTextLikeFileType) {
            for (matcher in fileTypeManager.getAssociations(type!!)) {
              extensions.add(matcher.presentableString)
            }
          }
        }
        writer.obj {
          writeList(writer, "modules", modules)
          writeList(writer, "plugins", pluginIds)
          writeList(writer, "extensions", extensions)
        }
      }
    }
    catch (e: Exception) {
      e.printStackTrace(System.err)
      exitProcess(1)
    }
    exitProcess(0)
  }
}

private fun writeList(writer: JsonGenerator, name: String, elements: List<String>) {
  writer.array(name) {
    for (module in elements) {
      writer.writeString(module)
    }
  }
}