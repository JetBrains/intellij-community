// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.fileTemplates.impl

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.objectTree.ThrowableInterner
import com.intellij.project.stateStore
import com.intellij.util.ReflectionUtil
import com.intellij.util.ResourceUtil
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import org.apache.velocity.runtime.ParserPool
import org.apache.velocity.runtime.RuntimeSingleton
import org.apache.velocity.runtime.directive.Stop
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.text.MessageFormat
import java.util.*
import java.util.function.BiPredicate
import java.util.function.Function
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
    const val TEMPLATES_DIR = "fileTemplates"
  }

  private val managers = SynchronizedClearableLazy { loadConfiguration(project) }

  val allManagers: Collection<FTManager>
    get() = managers.value.managers.values

  val defaultTemplatesManager: FTManager
    get() = FTManager(managers.value.getManager(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY))

  val internalTemplatesManager: FTManager
    get() = FTManager(managers.value.getManager(FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY))

  val patternsManager: FTManager
    get() = FTManager(managers.value.getManager(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY))

  val codeTemplatesManager: FTManager
    get() = FTManager(managers.value.getManager(FileTemplateManager.CODE_TEMPLATES_CATEGORY))

  val j2eeTemplatesManager: FTManager
    get() = FTManager(managers.value.getManager(FileTemplateManager.J2EE_TEMPLATES_CATEGORY))

  val defaultTemplateDescription: Supplier<String>?
    get() = managers.value.defaultTemplateDescription

  val defaultIncludeDescription: Supplier<String>?
    get() = managers.value.defaultIncludeDescription

  init {
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
        }.getOrLogException(logger<FileTemplatesLoader>())
      }

      private fun resetParserPool() {
        runCatching {
          val ppField = ReflectionUtil.getDeclaredField(RuntimeSingleton.getRuntimeServices().javaClass, "parserPool") ?: return
          (ppField.get(RuntimeSingleton.getRuntimeServices()) as? ParserPool)?.initialize(RuntimeSingleton.getRuntimeServices())
        }.getOrLogException(logger<FileTemplatesLoader>())
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

// example: templateName="NewClass"   templateExtension="java"
private fun getDescriptionPath(pathPrefix: String,
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

private fun loadConfiguration(project: Project?): LoadedConfiguration {
  val configDir = if (project == null || project.isDefault) {
    PathManager.getConfigDir().resolve(FileTemplatesLoader.TEMPLATES_DIR)
  }
  else {
    project.stateStore.projectFilePath.parent.resolve(FileTemplatesLoader.TEMPLATES_DIR)
  }

  // not a map - force predefined order for stable performance results
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
    val manager = FTManager(name, configDir.resolve(pathPrefix), result.prefixToTemplates.get(pathPrefix) ?: emptyList(),
                            name == FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY)
    manager.loadCustomizedContent()
    managers.put(name, manager)
  }
  return LoadedConfiguration(managers = managers,
                             defaultTemplateDescription = result.defaultTemplateDescription,
                             defaultIncludeDescription = result.defaultIncludeDescription)
}

private fun loadDefaultTemplates(prefixes: List<String>): FileTemplateLoadResult {
  val result = FileTemplateLoadResult(HashMap())
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
          loadDefaultsFromJar(url, prefixes, result)
        }
        else if (URLUtil.FILE_PROTOCOL.equals(protocol, ignoreCase = true)) {
          loadDefaultsFromDirectory(url, result, prefixes)
        }
      }
    }
    catch (e: IOException) {
      logger<FileTemplatesLoader>().error(e)
    }
  }
  return result
}

private fun loadDefaultsFromJar(url: URL, prefixes: List<String>, result: FileTemplateLoadResult) {
  val children = UrlUtil.getChildPathsFromJar(url)
  if (children.isEmpty()) {
    return
  }

  val descriptionPaths: MutableSet<String> = HashSet()
  for (path in children) {
    if (path == "default.html") {
      result.defaultTemplateDescription = createSupplierForUrlSource(url, path)
    }
    else if (path == "includes/default.html") {
      result.defaultIncludeDescription = createSupplierForUrlSource(url, path)
    }
    else if (path.endsWith(DESCRIPTION_EXTENSION_SUFFIX)) {
      descriptionPaths.add(path)
    }
  }

  processTemplates(files = children.asSequence().filter { it.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX) },
                   prefixes = prefixes,
                   descriptionPaths = descriptionPaths,
                   result = result,
                   descriptionLoader = { loadTemplate(url, it) }) {
    createSupplierForUrlSource(url, it)
  }
}

private inline fun processTemplates(files: Sequence<String>,
                                    prefixes: List<String>,
                                    descriptionPaths: MutableSet<String>,
                                    result: FileTemplateLoadResult,
                                    descriptionLoader: Function<String, String>,
                                    dataLoader: (path: String) -> Supplier<String>) {
  for (path in files) {
    val prefix = prefixes.firstOrNull {
      if (it.isEmpty()) {
        !path.contains('/')
      }
      else {
        path.length > it.length && path[it.length] == '/' && path.startsWith(it) && path.indexOf('/', it.length + 1) == -1
      }
    } ?: continue

    val filename = path.substring(if (prefix.isEmpty()) 0 else prefix.length + 1, path.length - FTManager.TEMPLATE_EXTENSION_SUFFIX.length)
    val extension = FileUtilRt.getExtension(filename)
    val templateName = filename.substring(0, filename.length - extension.length - 1)
    val descriptionPath = getDescriptionPath(prefix, templateName, extension, descriptionPaths)
    val template = DefaultTemplate(name = templateName,
                                   extension = extension,
                                   textSupplier = dataLoader(path),
                                   descriptionLoader = descriptionLoader.takeIf { descriptionPath != null },
                                   descriptionPath = descriptionPath)
    result.prefixToTemplates.computeIfAbsent(prefix) { mutableListOf() }.add(template)
  }
}

private fun loadDefaultsFromDirectory(root: URL, result: FileTemplateLoadResult, prefixes: List<String>) {
  val descriptionPaths = HashSet<String>()
  val templateFiles = mutableListOf<String>()
  val rootFile = urlToPath(root)

  Files.find(rootFile, Int.MAX_VALUE, BiPredicate { _, a -> a.isRegularFile }).use {
    it.forEach { file ->
      val path = rootFile.relativize(file).toString().replace(File.separatorChar, '/')
      if (path.endsWith("/default.html")) {
        result.defaultTemplateDescription = Supplier { Files.readString(file) }
      }
      else if (path.endsWith("/includes/default.html")) {
        result.defaultIncludeDescription = Supplier { Files.readString(file) }
      }
      else if (path.endsWith(".html")) {
        descriptionPaths.add(path)
      }
      else if (path.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX)) {
        templateFiles.add(path)
      }
    }
  }

  processTemplates(files = templateFiles.asSequence(),
                   prefixes = prefixes,
                   descriptionPaths = descriptionPaths,
                   result = result,
                   descriptionLoader = { Files.readString(rootFile.resolve(it)) }) {
    Supplier { Files.readString(rootFile.resolve(it)) }
  }
}

private fun urlToPath(root: URL): Path {
  var path = root.toURI().path
  if (SystemInfoRt.isWindows && path.startsWith("/")) {
    // trim leading slashes before drive letter
    val position = path.indexOf(':')
    if (position > 1) {
      path = path.substring(position - 1)
    }
  }
  return Path.of(path)
}

private class FileTemplateLoadResult(@JvmField val prefixToTemplates: MutableMap<String, MutableList<DefaultTemplate>>) {
  @JvmField
  var defaultTemplateDescription: Supplier<String>? = null

  @JvmField
  var defaultIncludeDescription: Supplier<String>? = null
}

private class LoadedConfiguration(@JvmField val managers: Map<String, FTManager>,
                                  @JvmField val defaultTemplateDescription: Supplier<String>?,
                                  @JvmField val defaultIncludeDescription: Supplier<String>?) {
  fun getManager(kind: String) = managers.get(kind)!!
}

private fun createSupplierForUrlSource(root: URL, path: String): Supplier<String> {
  // no need to cache the result - it is client responsibility, see DefaultTemplate.getDescriptionText for example
  return object : Supplier<String> {
    override fun get() = loadTemplate(root, path)

    override fun toString() = "(root=$root, path=$path)"
  }
}

private fun loadTemplate(root: URL, path: String): String {
  // root url should be used as context to use provided handler to load data to avoid using a generic one
  return try {
    ResourceUtil.loadText(URL(root, "${FileTemplatesLoader.TEMPLATES_DIR}/${path.trimEnd('/')}").openStream())
  }
  catch (e: IOException) {
    logger<FileTemplatesLoader>().error(e)
    ""
  }
}