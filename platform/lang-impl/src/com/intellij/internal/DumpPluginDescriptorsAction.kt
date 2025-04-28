// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.relativeTo

private class DumpPluginDescriptorsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    service<PluginDescriptionDumper>().dump(e.project)
  }
  
  @Service
  private class PluginDescriptionDumper(private val coroutineScope: CoroutineScope) {
    fun dump(project: Project?) {
      coroutineScope.launch {
        val targetFile = PathManager.getLogDir().resolve("plugin-descriptors-data.json")
        withContext(Dispatchers.IO) {
          targetFile.bufferedWriter().use { out ->
            val writer = JsonFactory().createGenerator(out).setPrettyPrinter(DefaultPrettyPrinter().withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE))
            writer.writeStartArray()
            writer.writePlugins()
            writer.writeEndArray()
            writer.close()
          }
        }
        if (project != null) {
          val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(targetFile)
          if (virtualFile != null) {
            withContext(Dispatchers.EDT) {
              if (!project.isDisposed) {
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
              }
            }
          }
        }
      }
    }

    private fun JsonGenerator.writePlugins() {
      val allPlugins = PluginManager.getPlugins()

      val pluginClassLoaders = allPlugins.mapNotNullTo(HashSet()) { it.pluginClassLoader }
      allPlugins.flatMapTo(pluginClassLoaders) { 
        (it as? IdeaPluginDescriptorImpl)?.content?.modules?.mapNotNull { it.requireDescriptor().pluginClassLoader } ?: emptyList() 
      }
      @Suppress("TestOnlyProblems")
      val parentClassLoaders = pluginClassLoaders.filterIsInstance<PluginClassLoader>()
                                  .flatMapTo(HashSet()) { classLoader -> classLoader._getParents().mapNotNull { it.pluginClassLoader } }
      val coreClassLoader = ClassLoaderConfigurator::class.java.classLoader
      parentClassLoaders.add(coreClassLoader)
      parentClassLoaders.add(ClassLoader.getSystemClassLoader())
      parentClassLoaders.add(ClassLoader.getPlatformClassLoader())
      val nonPluginClassLoaders = parentClassLoaders.filterNot { it is PluginClassLoader }.withIndex().associateBy({ it.value }, { it.index })
      val classLoaderIds = parentClassLoaders.associateWith { classLoader ->
        when (classLoader) {
          is PluginClassLoader -> {
            val moduleSuffix = (classLoader.pluginDescriptor as? IdeaPluginDescriptorImpl)?.moduleName?.let { ":$it" } ?: ""
            "PluginClassLoader[${classLoader.pluginId.idString}$moduleSuffix]"
          }
          ClassLoader.getSystemClassLoader() -> "java.SystemClassLoader"
          ClassLoader.getPlatformClassLoader() -> "java.PlatformClassLoader"
          coreClassLoader -> "ij.CoreClassLoader"
          else -> "${classLoader.javaClass.simpleName}[${nonPluginClassLoaders[classLoader]}]"
        } + " @${Integer.toHexString(System.identityHashCode(classLoader))}" // errors reported by the JVM don't use classloader's `toString`, but instead only put the address tag
      }
      
      val printedClassLoaders = HashSet<ClassLoader>()
      PluginManager.getLoadedPlugins().forEach { plugin ->
        writePluginData(plugin, classLoaderIds, printedClassLoaders)
      }
      allPlugins.filterNot { it.isEnabled }.sortedBy { it.pluginId.idString }.forEach { plugin ->
        writePluginData(plugin, classLoaderIds, printedClassLoaders)
      }
    }

    private fun JsonGenerator.writePluginData(plugin: IdeaPluginDescriptor,
                                              classLoaderIds: Map<ClassLoader, String>,
                                              printedClassLoaders: MutableSet<ClassLoader>) {
      writeStartObject()
      writeStringField("id", plugin.pluginId.idString)
      writeBooleanField("enabled", plugin.isEnabled)
      writeBooleanField("bundled", plugin.isBundled)
      if (plugin.isEnabled) {
        writeClassLoaderData(plugin.classLoader, classLoaderIds, printedClassLoaders)
      }
      writePluginModulesData(plugin, classLoaderIds, printedClassLoaders)
      writeEndObject()
    }

    private fun JsonGenerator.writePluginModulesData(plugin: IdeaPluginDescriptor,
                                                     classLoaderIds: Map<ClassLoader, String>,
                                                     printedClassLoaders: MutableSet<ClassLoader>) {
      if (plugin !is IdeaPluginDescriptorImpl) return
      val modules = plugin.content.modules
      if (modules.isEmpty()) return
      
      writeArrayFieldStart("modules")
      for (moduleItem in modules) {
        writeStartObject()
        writeStringField("name", moduleItem.name)
        val descriptor = moduleItem.requireDescriptor()
        val isEnabled = descriptor in PluginManagerCore.getPluginSet().getEnabledModules()
        writeBooleanField("enabled", isEnabled)
        if (isEnabled) {
          writeClassLoaderData(descriptor.classLoader, classLoaderIds, printedClassLoaders)
        }
        writeEndObject()
      }
      writeEndArray()
    }

    private fun JsonGenerator.writeClassLoaderData(classLoader: ClassLoader,
                                                   classLoaderIds: Map<ClassLoader, String>,
                                                   printedClassLoaders: MutableSet<ClassLoader>) {
      writeFieldName("classLoader")
      writeStartObject()
      classLoaderIds[classLoader]?.let {
        writeStringField("id", it)
      }
      if (printedClassLoaders.add(classLoader)) {
        @Suppress("TestOnlyProblems")
        val parents = when (classLoader) {
          is PluginClassLoader -> classLoader.getAllParentsClassLoaders().toList()
          else -> listOf(classLoader.parent)
        }
        writeArrayFieldStart("parents")
        for (parent in parents) {
          writeString(classLoaderIds[parent] ?: parent.toString())
        }
        writeEndArray()

        writeArrayFieldStart("classpath")
        val homePath = Path.of(PathManager.getHomePath())
        if (classLoader is UrlClassLoader) {
          for (path in classLoader.baseUrls) {
            val relativePath = if (path.startsWith(homePath)) path.relativeTo(homePath) else path
            writeString(relativePath.toString())
          }
        }
        else {
          writeString("unknown")
        }
        writeEndArray()
      }
      writeEndObject()
    }

  }
}
