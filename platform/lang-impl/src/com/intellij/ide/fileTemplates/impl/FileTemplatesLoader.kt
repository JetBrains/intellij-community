// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.fileTemplates.impl

import com.intellij.configurationStore.StreamProvider
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.impl.stores.ComponentStoreOwner
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
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
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.BiPredicate
import java.util.function.Function
import java.util.function.Supplier
import kotlin.io.path.invariantSeparatorsPathString

private const val DEFAULT_TEMPLATES_ROOT = FileTemplatesLoader.TEMPLATES_DIR
private const val DESCRIPTION_FILE_EXTENSION = "html"
private const val DESCRIPTION_EXTENSION_SUFFIX = ".$DESCRIPTION_FILE_EXTENSION"

/**
 * Serves as a container for all existing template manager types and loads corresponding templates lazily.
 * Reloads templates on plugin change.
 */
internal open class FileTemplatesLoader(project: Project?) : Disposable {
  companion object {
    const val TEMPLATES_DIR: String = "fileTemplates"
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
        reloadTemplates()
      }

      override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        reloadTemplates()
      }
    })
  }

  protected open fun reloadTemplates() {
    managers.drop()
  }

  override fun dispose() {
  }
}

// example: templateName="NewClass"   templateExtension="java"
private fun getDescriptionPath(pathPrefix: String,
                               templateName: String,
                               templateExtension: String,
                               descriptionPaths: Set<String>): String? {
  val name = "$templateName.$templateExtension$DESCRIPTION_EXTENSION_SUFFIX"
  val path = if (pathPrefix.isEmpty()) name else "$pathPrefix/$name"
  return if (descriptionPaths.contains(path)) path else null
}

private fun loadConfiguration(project: Project?): LoadedConfiguration {
  val templatePath = Path.of(FileTemplatesLoader.TEMPLATES_DIR)
  val configDir = if (project == null || project.isDefault) {
    PathManager.getConfigDir().resolve(templatePath)
  }
  else {
    project.stateStore.projectFilePath.parent.resolve(templatePath)
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
  val streamProvider = streamProvider(project)
  for ((name, pathPrefix) in managerToDir) {
    val manager = FTManager(
      name,
      templatePath.resolve(pathPrefix),
      configDir.resolve(pathPrefix),
      result.prefixToTemplates.get(pathPrefix) ?: emptyList(),
      name == FileTemplateManager.INTERNAL_TEMPLATES_CATEGORY,
      streamProvider,
    )
    manager.loadCustomizedContent()
    managers.put(name, manager)
  }
  return LoadedConfiguration(
    managers = managers,
    defaultTemplateDescription = result.defaultTemplateDescription,
    defaultIncludeDescription = result.defaultIncludeDescription,
  )
}

internal fun streamProvider(project: Project?): StreamProvider {
  return ((project ?: ApplicationManager.getApplication()) as ComponentStoreOwner).componentStore.storageManager.streamProvider
}

private fun loadDefaultTemplates(prefixes: List<String>): FileTemplateLoadResult {
  val result = FileTemplateLoadResult(HashMap())
  val processedUrls = HashSet<URL>()
  val processedLoaders = Collections.newSetFromMap(IdentityHashMap<ClassLoader, Boolean>())
  for (module in PluginManagerCore.getPluginSet().getEnabledModules()) {
    val loader = module.classLoader
    if ((loader is PluginAwareClassLoader && loader.files.isEmpty()) || !processedLoaders.add(loader)) {
      // test or development mode, when IDEA_CORE's loader contains all the classpath
      continue
    }

    if (module.moduleName != null && module.jarFiles.isNullOrEmpty()) {
      // not isolated module - skip, as resource will be loaded from plugin classpath
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
          loadDefaultsFromJar(module = module, url = url, prefixes = prefixes, result = result)
        }
        else if (URLUtil.FILE_PROTOCOL.equals(protocol, ignoreCase = true)) {
          loadDefaultsFromDirectory(module = module, root = url, result = result, prefixes = prefixes)
        }
      }
    }
    catch (e: IOException) {
      logger<FileTemplatesLoader>().error(e)
    }
  }
  return result
}

private fun loadDefaultsFromJar(module: PluginDescriptor, url: URL, prefixes: List<String>, result: FileTemplateLoadResult) {
  val children = UrlUtil.getChildPathsFromJar(url)
  val classLoader = module.classLoader
  if (children.isEmpty()) {
    return
  }

  val descriptionPaths = HashSet<String>()
  for (path in children) {
    if (path.endsWith("includes/default.html")) {
      result.defaultIncludeDescription = Supplier { getContentOrLogError(path) { loadLocalizedContent(classLoader, url, it) } }
    }
    else if (path.endsWith("default.html")) {
      result.defaultTemplateDescription = Supplier { getContentOrLogError(path) { loadLocalizedContent(classLoader, url, it) } }
    }
    else if (path.endsWith(DESCRIPTION_EXTENSION_SUFFIX)) {
      val filePath = Path.of(path).invariantSeparatorsPathString
      descriptionPaths.add(filePath)
    }
  }

  processTemplates(
    module = module,
    files = children.asSequence().filter { it.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX) },
    prefixes = prefixes,
    descriptionPaths = descriptionPaths,
    result = result,
    descriptionLoader = { loadLocalizedContent(classLoader, url, it) },
    dataLoader = { loadLocalizedContent(classLoader, url, it) },
  )
}

private fun processTemplates(
  module: PluginDescriptor,
  files: Sequence<String>,
  prefixes: List<String>,
  descriptionPaths: MutableSet<String>,
  result: FileTemplateLoadResult,
  descriptionLoader: Function<String, String?>,
  dataLoader: Function<String, String?>,
) {
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

    // can be empty, e.g., Dockerfile
    val templateName = if (extension.isNotEmpty()) {
      filename.substring(0, filename.length - extension.length - 1)
    }
    else {
      filename
    }

    val descriptionPath = getDescriptionPath(prefix, templateName, extension, descriptionPaths)
    val template = DefaultTemplate(
      name = templateName,
      extension = extension,
      textLoader = dataLoader,
      descriptionLoader = descriptionLoader.takeIf { descriptionPath != null },
      descriptionPath = descriptionPath,
      templatePath = Path.of(DEFAULT_TEMPLATES_ROOT).resolve(path),
      pluginDescriptor = module,
    )
    result.prefixToTemplates.computeIfAbsent(prefix) { mutableListOf() }.add(template)
  }
}

private fun loadDefaultsFromDirectory(module: PluginDescriptor, root: URL, result: FileTemplateLoadResult, prefixes: List<String>) {
  val descriptionPaths = HashSet<String>()
  val templateFiles = mutableListOf<String>()
  val pathToFileTemplate = urlToPath(root)
  val rootFolder = pathToFileTemplate.parent
  val classLoader = module.classLoader
  Files.find(pathToFileTemplate, Int.MAX_VALUE, BiPredicate { _, a -> a.isRegularFile }).use { stream ->
    stream.forEach { file ->
      val path = pathToFileTemplate.relativize(file).invariantSeparatorsPathString
      when {
        path.endsWith("includes/default.html") -> {
          result.defaultIncludeDescription = Supplier { getContentOrLogError(path) { filePath -> loadLocalizedContent(classLoader, rootFolder, filePath) } }
        }
        path.endsWith("default.html") -> {
          result.defaultTemplateDescription = Supplier { getContentOrLogError(path) { filePath -> loadLocalizedContent(classLoader, rootFolder, filePath) } }
        }
        path.endsWith(DESCRIPTION_EXTENSION_SUFFIX) -> {
          descriptionPaths.add(path)
        }
        path.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX) -> {
          templateFiles.add(path)
        }
      }
    }
  }

  processTemplates(
    module = module,
    files = templateFiles.asSequence(),
    prefixes = prefixes,
    descriptionPaths = descriptionPaths,
    result = result,
    descriptionLoader = { loadLocalizedContent(classLoader, rootFolder, it) },
    dataLoader = { loadLocalizedContent(classLoader, rootFolder, it) },
  )
}

private fun getContentOrLogError(path: String, pathResolver: Function<String, String?>): String {
  val fullPath = Path.of(DEFAULT_TEMPLATES_ROOT).resolve(path).invariantSeparatorsPathString
  val result = pathResolver.apply(fullPath)
  if (result == null) {
    logger<FileTemplatesLoader>().error("Cannot find file by path: $path")
    return "Unexpected error occurred"
  }
  return result
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

private fun loadLocalizedContent(classLoader: ClassLoader, root: Any, path: String): String? {
  var result: String?
  val locale = LocalizationUtil.getLocaleOrNullForDefault()
  if (locale != null) {
    //loading from source files with localization folder/suffix
    val localizedPaths = LocalizationUtil.getLocalizedPaths(path)
    for (localizedPath in localizedPaths) {
      result = loadFileContent(classLoader, root, localizedPath)
      if (!result.isNullOrEmpty()) {
        return result
      }
    }
    //loading from localization plugin
    result = LocalizationUtil.getPluginClassLoader()?.let {
      ResourceUtil.getResourceAsBytesSafely(path, it)?.toString(StandardCharsets.UTF_8)
    }  ?: loadFileContent(classLoader, root, path)
    //loading localized content from source files
    if (!result.isNullOrEmpty()) return result
  }
  //default loading content
  result = loadFileContent(classLoader, root, path)
  return result
}

private fun loadFileContent(classLoader: ClassLoader, root: Any, path: String): String? {
  var result: String? = null
  try {
    result = ResourceUtil.getResourceAsBytesSafely(path, classLoader)?.toString(StandardCharsets.UTF_8)
    if (!result.isNullOrEmpty()) {
      return result
    }

    when (root) {
      is URL -> {
        val url = URL(root.protocol, root.host, root.port, root.path.replace(DEFAULT_TEMPLATES_ROOT, path))
        result = ResourceUtil.loadText(url.openStream())
      }
      is Path -> {
        result = Files.readString(root.resolve(path))
      }
    }
  }
  catch (e: IOException) {
    logger<FileTemplatesLoader>().info(e.message)
  }
  return result
}
