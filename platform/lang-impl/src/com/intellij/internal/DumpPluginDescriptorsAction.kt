// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.internal

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.plugins.ClassLoaderConfigurator
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.contentModuleName
import com.intellij.ide.plugins.contentModules
import com.intellij.internal.PluginDescriptionDumper.Companion.getDumpFileLocation
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tools.jackson.core.JsonGenerator
import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.json.JsonFactory
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.bufferedWriter
import kotlin.io.path.relativeTo

private const val DUMP_DESCRIPTORS_PROPERTY = "idea.dump.plugin.descriptors"

internal class DumpPluginDescriptorsAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Duplicated {
  override fun actionPerformed(e: AnActionEvent) {
    service<PluginDescriptionDumper>().dump(getDumpFileLocation(), e.project)
  }
}

internal class DumpPluginDescriptorsOnAppStartTrigger : ApplicationActivity {
  override suspend fun execute() {
    if (System.getProperty(DUMP_DESCRIPTORS_PROPERTY, "false") == "true") {
      val dumpPath = getDumpFileLocation()
      val dumper = serviceAsync<PluginDescriptionDumper>()
      dumper.coroutineScope.launch {
        dumper.dump(dumpPath, null)
        logger<DumpPluginDescriptorsAction>().warn("Plugin descriptors data dumped to ${dumpPath}")
      }
    }
  }
}

@Service
private class PluginDescriptionDumper(val coroutineScope: CoroutineScope) {
  companion object {
    fun getDumpFileLocation(): Path {
      val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
      return PathManager.getLogDir().resolve("plugin-descriptors-data-$timestamp.json")
    }
  }

  fun dump(dumpPath: Path, project: Project?) {
    coroutineScope.launch {
      withContext(Dispatchers.IO) {
        dumpPath.bufferedWriter().use { out ->
          val writer = JsonFactory().createGenerator(ObjectWriteContext.empty(), out)
          writer.writeStartArray()
          writer.writePlugins()
          writer.writeEndArray()
          writer.close()
        }
      }
      if (project != null) {
        val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(dumpPath)
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
      (it as? IdeaPluginDescriptorImpl)?.contentModules?.mapNotNull { it.pluginClassLoader } ?: emptyList()
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
          val moduleSuffix = (classLoader.pluginDescriptor as? IdeaPluginDescriptorImpl)?.contentModuleName?.let { ":$it" } ?: ""
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
    writeStringProperty("id", plugin.pluginId.idString)
    writeBooleanProperty("enabled", plugin.isEnabled)
    writeBooleanProperty("bundled", plugin.isBundled)
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
    val modules = plugin.contentModules
    if (modules.isEmpty()) return

    writeArrayPropertyStart("modules")
    for (module in modules) {
      writeStartObject()
      writeStringProperty("name", module.moduleId.name)
      val isEnabled = module in PluginManagerCore.getPluginSet().getEnabledModules()
      writeBooleanProperty("enabled", isEnabled)
      if (isEnabled) {
        writeClassLoaderData(module.classLoader, classLoaderIds, printedClassLoaders)
      }
      writeEndObject()
    }
    writeEndArray()
  }

  private fun JsonGenerator.writeClassLoaderData(classLoader: ClassLoader,
                                                 classLoaderIds: Map<ClassLoader, String>,
                                                 printedClassLoaders: MutableSet<ClassLoader>) {
    writeName("classLoader")
    writeStartObject()
    classLoaderIds[classLoader]?.let {
      writeStringProperty("id", it)
    }
    if (printedClassLoaders.add(classLoader)) {
      @Suppress("TestOnlyProblems")
      val parents = when (classLoader) {
        is PluginClassLoader -> classLoader.getAllParentsClassLoaders().toList()
        else -> listOf(classLoader.parent)
      }
      writeArrayPropertyStart("parents")
      for (parent in parents) {
        writeString(classLoaderIds[parent] ?: parent.toString())
      }
      writeEndArray()

      writeArrayPropertyStart("classpath")
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
