// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.diagnostic.logger
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

private class BundledPluginsLister : ModernApplicationStarter() {
  override val commandName: String
    get() = "listBundledPlugins"

  // not premain because FileTypeManager is used to report extensions
  override suspend fun start(args: List<String>) {
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
      JsonFactory().createGenerator(out).use { writer ->
        val plugins = PluginManagerCore.getPluginSet().enabledPlugins
        val modules = HashSet<String>()
        val pluginIds = ArrayList<String>(plugins.size)
        for (plugin in plugins) {
          pluginIds.add(plugin.pluginId.idString)
          plugin.modules.mapTo(modules) { it.idString }
          plugin.content.modules.mapTo(modules) { it.name }
        }
        pluginIds.sort()
        val fileTypeManager = FileTypeManager.getInstance()
        val extensions = ArrayList<String>()
        for (type in fileTypeManager.registeredFileTypes) {
          if (type !is PlainTextLikeFileType) {
            for (matcher in fileTypeManager.getAssociations(type!!)) {
              extensions.add(matcher.presentableString)
            }
          }
        }
        extensions.sort()
        writer.obj {
          writeList(writer, "modules", modules.sorted())
          writeList(writer, "plugins", pluginIds)
          writeList(writer, "fileExtensions", extensions)
        }
      }
    }
    catch (e: Exception) {
      try {
        logger<BundledPluginsLister>().error("Bundled plugins list builder failed", e)
      }
      catch (ignored: Throwable) { }
      e.printStackTrace(System.err)
      exitProcess(1)
    }
    exitProcess(0)
  }
}

private fun writeList(writer: JsonGenerator, name: String, elements: Collection<String>) {
  writer.array(name) {
    for (module in elements) {
      writer.writeString(module)
    }
  }
}