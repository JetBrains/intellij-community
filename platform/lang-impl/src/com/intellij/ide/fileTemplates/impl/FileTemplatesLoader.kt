// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.fileTemplates.impl

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.FileTemplateLoadResult.Companion.createSupplier
import com.intellij.ide.fileTemplates.impl.FileTemplateLoadResult.Companion.processDirectory
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.objectTree.ThrowableInterner
import com.intellij.project.stateStore
import com.intellij.util.Function
import com.intellij.util.ReflectionUtil
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import org.apache.velocity.runtime.ParserPool
import org.apache.velocity.runtime.RuntimeSingleton
import org.apache.velocity.runtime.directive.Stop
import java.io.IOException
import java.net.URL
import java.text.MessageFormat
import java.util.*
import java.util.function.Supplier

private const val DEFAULT_TEMPLATES_ROOT = FileTemplatesLoader.TEMPLATES_DIR
private const val DESCRIPTION_FILE_EXTENSION = "html"
private const val DESCRIPTION_EXTENSION_SUFFIX = ".$DESCRIPTION_FILE_EXTENSION"

/**
 * Serves as a container for all existing template manager types and loads corresponding templates lazily.
 * Reloads templates on plugins change.
 */
internal open class FileTemplatesLoader(project: Project?) : Disposable {
  companion object {
    private val LOG = Logger.getInstance(FileTemplatesLoader::class.java)
    const val TEMPLATES_DIR = "fileTemplates"

    fun matchesPrefix(path: String, prefix: String): Boolean {
      return if (prefix.isEmpty()) {
        path.indexOf('/') == -1
      }
      else FileUtil.startsWith(path, prefix) && path.indexOf('/', prefix.length + 1) == -1
    }

    //Example: templateName="NewClass"   templateExtension="java"
    fun getDescriptionPath(pathPrefix: String,
                           templateName: String,
                           templateExtension: String,
                           descriptionPaths: Set<String>): String? {
      val locale = Locale.getDefault()
      var name = MessageFormat.format("{0}.{1}_{2}_{3}$DESCRIPTION_EXTENSION_SUFFIX",
                                      templateName,
                                      templateExtension,
                                      locale.language,
                                      locale.country)
      var path = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name"
      if (descriptionPaths.contains(path)) {
        return path
      }

      name = MessageFormat.format("{0}.{1}_{2}$DESCRIPTION_EXTENSION_SUFFIX", templateName, templateExtension, locale.language)
      path = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name"
      if (descriptionPaths.contains(path)) {
        return path
      }

      name = "$templateName.$templateExtension$DESCRIPTION_EXTENSION_SUFFIX"
      path = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name"
      return if (descriptionPaths.contains(path)) path else null
    }
  }

  private val managers: SynchronizedClearableLazy<LoadedConfiguration>

  val allManagers: Collection<FTManager>
    get() = managers.value.managers.values

  val defaultTemplatesManager: FTManager
    get() = FTManager(managers.value.getManager(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY)!!)

  val internalTemplatesManager: FTManager
    get() = FTManager(managers.value.getManager(FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY)!!)

  val patternsManager: FTManager
    get() = FTManager(managers.value.getManager(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY)!!)

  val codeTemplatesManager: FTManager
    get() = FTManager(managers.value.getManager(FileTemplateManager.CODE_TEMPLATES_CATEGORY)!!)

  val j2eeTemplatesManager: FTManager
    get() = FTManager(managers.value.getManager(FileTemplateManager.J2EE_TEMPLATES_CATEGORY)!!)

  val defaultTemplateDescription: Supplier<String>?
    get() = managers.value.defaultTemplateDescription

  val defaultIncludeDescription: Supplier<String>?
    get() = managers.value.defaultIncludeDescription

  init {
    managers = SynchronizedClearableLazy { loadConfiguration(project) }
    @Suppress("LeakingThis")
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        // this shouldn't be necessary once we update to a new Velocity Engine with this leak fixed (IDEA-240449, IDEABKL-7932)
        clearClassLeakViaStaticExceptionTrace()
        resetParserPool()
      }

      private fun clearClassLeakViaStaticExceptionTrace() {
        val field = ReflectionUtil.getDeclaredField(Stop::class.java, "STOP_ALL") ?: return
        runCatching {
          ThrowableInterner.clearBacktrace((field.get(null) as Throwable))
        }.getOrLogException(LOG)
      }

      private fun resetParserPool() {
        runCatching {
          val ppField = ReflectionUtil.getDeclaredField(RuntimeSingleton.getRuntimeServices().javaClass, "parserPool") ?: return
          (ppField.get(RuntimeSingleton.getRuntimeServices()) as? ParserPool)?.initialize(RuntimeSingleton.getRuntimeServices())
        }.getOrLogException(LOG)
      }

      override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        managers.drop()
      }

      override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        managers.drop()
      }
    })
  }

  override fun dispose() {
  }
}

private fun loadConfiguration(project: Project?): LoadedConfiguration {
  val configDir = if (project == null || project.isDefault) {
    PathManager.getConfigDir().resolve(FileTemplatesLoader.TEMPLATES_DIR)
  }
  else {
    project.stateStore.projectFilePath.parent.resolve(FileTemplatesLoader.TEMPLATES_DIR)
  }

  // not a map - force predefined order for stabe performance results
  val managerToDir = listOf(
    FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY to "",
    FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY to "internal",
    FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY to "includes",
    FileTemplateManager.CODE_TEMPLATES_CATEGORY to "code",
    FileTemplateManager.J2EE_TEMPLATES_CATEGORY to "j2ee"
  )

  val result = loadDefaultTemplates(managerToDir.map { it.second })
  val managers = HashMap<String, FTManager>(managerToDir.size)
  for ((name, pathPrefix) in managerToDir) {
    val manager = FTManager(name, configDir.resolve(pathPrefix), name == FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY)
    manager.setDefaultTemplates(result.result.get(pathPrefix))
    manager.loadCustomizedContent()
    managers.put(name, manager)
  }
  return LoadedConfiguration(managers = managers,
                             defaultTemplateDescription = result.defaultTemplateDescription,
                             defaultIncludeDescription = result.defaultIncludeDescription)
}

private fun loadDefaultTemplates(prefixes: List<String>): FileTemplateLoadResult {
  val result = FileTemplateLoadResult(MultiMap())
  val processedUrls = HashSet<URL>()
  val processedLoaders = Collections.newSetFromMap(IdentityHashMap<ClassLoader, Boolean>())
  for (plugin in PluginManagerCore.getPluginSet().enabledPlugins) {
    val loader = plugin.classLoader
    if (loader is PluginAwareClassLoader && (loader as PluginAwareClassLoader).files.isEmpty() || !processedLoaders.add(loader)) {
      // test or development mode, when IDEA_CORE's loader contains all the classpath
      continue
    }

    try {
      val resourceUrls = if (loader is UrlClassLoader) {
        // don't use parents from plugin class loader - we process all plugins
        loader.classPath.getResources(DEFAULT_TEMPLATES_ROOT)
      }
      else {
        loader.getResources(DEFAULT_TEMPLATES_ROOT)
      }

      while (resourceUrls.hasMoreElements()) {
        val url = resourceUrls.nextElement()
        if (!processedUrls.add(url)) {
          continue
        }

        val protocol = url.protocol
        if (URLUtil.JAR_PROTOCOL.equals(protocol, ignoreCase = true)) {
          val children = UrlUtil.getChildPathsFromJar(url)
          if (!children.isEmpty()) {
            loadDefaultsFromRoot({ path: String? -> createSupplier(url, path!!) }, children, prefixes, result)
          }
        }
        else if (URLUtil.FILE_PROTOCOL.equals(protocol, ignoreCase = true)) {
          processDirectory(url, result, prefixes)
        }
      }
    }
    catch (e: IOException) {
      logger<FileTemplatesLoader>().error(e)
    }
  }
  return result
}

private fun loadDefaultsFromRoot(dataProducer: Function<String, Supplier<String>>,
                                 children: List<String>,
                                 prefixes: List<String>,
                                 result: FileTemplateLoadResult) {
  val descriptionPaths: MutableSet<String> = HashSet()
  for (path in children) {
    if (path == "default.html") {
      result.defaultTemplateDescription = dataProducer.`fun`(path)
    }
    else if (path == "includes/default.html") {
      result.defaultIncludeDescription = dataProducer.`fun`(path)
    }
    else if (path.endsWith(DESCRIPTION_EXTENSION_SUFFIX)) {
      descriptionPaths.add(path)
    }
  }
  for (path in children) {
    if (!path.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX)) {
      continue
    }
    for (prefix in prefixes) {
      if (!FileTemplatesLoader.matchesPrefix(path, prefix)) {
        continue
      }
      val filename = path.substring(if (prefix.isEmpty()) 0 else prefix.length + 1,
                                    path.length - FTManager.TEMPLATE_EXTENSION_SUFFIX.length)
      val extension = FileUtilRt.getExtension(filename)
      val templateName = filename.substring(0, filename.length - extension.length - 1)
      val descriptionPath = FileTemplatesLoader.getDescriptionPath(prefix, templateName, extension, descriptionPaths)
      val descriptionSupplier = if (descriptionPath == null) null else dataProducer.`fun`(descriptionPath)
      result.result.putValue(prefix,
                             DefaultTemplate(templateName, extension, dataProducer.`fun`(path), descriptionSupplier, descriptionPath))
      // FTManagers loop
      break
    }
  }
}

private class LoadedConfiguration(@JvmField val managers: Map<String, FTManager>,
                                  @JvmField val defaultTemplateDescription: Supplier<String>?,
                                  @JvmField val defaultIncludeDescription: Supplier<String>?) {
  fun getManager(kind: String) = managers.get(kind)
}