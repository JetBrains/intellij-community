// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
package com.intellij.ide.plugins

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.util.PlatformUtils
import com.intellij.util.io.Decompressor
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.*
import java.util.*
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.RecursiveAction
import java.util.concurrent.RecursiveTask
import java.util.function.Supplier

@ApiStatus.Internal
object PluginDescriptorLoader {
  fun loadDescriptor(file: Path,
                     isBundled: Boolean,
                     parentContext: DescriptorListLoadingContext): IdeaPluginDescriptorImpl? {
    return loadDescriptorFromFileOrDir(file = file,
                                       pathName = PluginManagerCore.PLUGIN_XML,
                                       context = parentContext,
                                       pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                       isBundled = isBundled,
                                       isEssential = false,
                                       isDirectory = Files.isDirectory(file))
  }

  internal fun loadForCoreEnv(pluginRoot: Path, fileName: String): IdeaPluginDescriptorImpl? {
    val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
    val parentContext = DescriptorListLoadingContext.createSingleDescriptorContext(DisabledPluginsState.disabledPlugins())
    if (Files.isDirectory(pluginRoot)) {
      return loadDescriptorFromDir(file = pluginRoot,
                                   descriptorRelativePath = "${PluginManagerCore.META_INF}$fileName",
                                   pluginPath = null,
                                   context = parentContext,
                                   isBundled = true,
                                   isEssential = true,
                                   pathResolver = pathResolver)
    }
    else {
      DescriptorLoadingContext().use { context ->
        return loadDescriptorFromJar(file = pluginRoot,
                                     fileName = fileName,
                                     pathResolver = pathResolver,
                                     context = context,
                                     parentContext = parentContext,
                                     isBundled = true,
                                     isEssential = true,
                                     pluginPath = null)
      }
    }
  }

  private fun loadDescriptorFromDir(file: Path,
                                    descriptorRelativePath: String,
                                    pluginPath: Path?,
                                    context: DescriptorListLoadingContext,
                                    isBundled: Boolean,
                                    isEssential: Boolean,
                                    pathResolver: PathResolver): IdeaPluginDescriptorImpl? {
    try {
      val descriptor = IdeaPluginDescriptorImpl(pluginPath ?: file, isBundled)
      val element = JDOMUtil.load(file.resolve(descriptorRelativePath), context.xmlFactory)
      descriptor.readExternal(element, pathResolver, context, descriptor, LocalFsDataLoader(file))
      return descriptor
    }
    catch (e: NoSuchFileException) {
      return null
    }
    catch (e: Throwable) {
      if (isEssential) {
        throw e
      }
      DescriptorListLoadingContext.LOG.warn("Cannot load ${file.resolve(descriptorRelativePath)}", e)
      return null
    }
  }

  internal fun loadDescriptorFromJar(file: Path,
                                    fileName: String,
                                    pathResolver: PathResolver,
                                    context: DescriptorLoadingContext,
                                    parentContext: DescriptorListLoadingContext,
                                    isBundled: Boolean,
                                    isEssential: Boolean,
                                    pluginPath: Path?): IdeaPluginDescriptorImpl? {
    val factory = parentContext.xmlFactory
    try {
      val element: Element
      val dataLoader: DataLoader
      try {
        val pool = ZipFilePool.POOL
        if (pool == null) {
          val fs = context.open(file)
          val pluginDescriptorFile = fs.getPath("/META-INF/$fileName")
          element = JDOMUtil.load(pluginDescriptorFile, factory)
          dataLoader = ZipFsDataLoader(pluginDescriptorFile.root)
        }
        else {
          val resolver = pool.load(file)
          val data = resolver.loadZipEntry("META-INF/$fileName") ?: return null
          element = JDOMUtil.load(data, factory)
          dataLoader = ImmutableZipFileDataLoader(resolver, file, pool)
        }
      }
      catch (ignore: NoSuchFileException) {
        return null
      }

      val descriptor = IdeaPluginDescriptorImpl(pluginPath ?: file, isBundled)
      if (descriptor.readExternal(element, pathResolver, parentContext, descriptor, dataLoader)) {
        descriptor.jarFiles = listOf(descriptor.pluginPath)
      }
      return descriptor
    }
    catch (e: Throwable) {
      if (isEssential) {
        throw e
      }
      parentContext.result.reportCannotLoad(file, e)
    }
    return null
  }

  @JvmStatic
  fun loadDescriptorFromFileOrDir(file: Path,
                                  pathName: String,
                                  context: DescriptorListLoadingContext,
                                  pathResolver: PathResolver,
                                  isBundled: Boolean,
                                  isEssential: Boolean,
                                  isDirectory: Boolean): IdeaPluginDescriptorImpl? {
    return when {
      isDirectory -> loadDescriptorFromDirAndNormalize(file = file,
                                                       pathName = pathName,
                                                       parentContext = context,
                                                       isBundled = isBundled,
                                                       isEssential = isEssential,
                                                       pathResolver = pathResolver)
      file.fileName.toString().endsWith(".jar", ignoreCase = true) -> {
        DescriptorLoadingContext().use { loadingContext ->
          loadDescriptorFromJar(file = file,
                                fileName = pathName,
                                pathResolver = pathResolver,
                                context = loadingContext,
                                parentContext = context,
                                isBundled = isBundled,
                                isEssential = isEssential,
                                pluginPath = null)
        }
      }
      else -> null
    }
  }

  internal fun loadDescriptorFromDirAndNormalize(file: Path,
                                                 pathName: String,
                                                 parentContext: DescriptorListLoadingContext,
                                                 isBundled: Boolean,
                                                 isEssential: Boolean,
                                                 pathResolver: PathResolver): IdeaPluginDescriptorImpl? {
    val descriptorRelativePath = "${PluginManagerCore.META_INF}$pathName"
    loadDescriptorFromDir(file = file,
                          descriptorRelativePath = descriptorRelativePath,
                          pluginPath = null,
                          context = parentContext,
                          isBundled = isBundled,
                          isEssential = isEssential,
                          pathResolver = pathResolver)?.let {
      return it
    }

    val pluginJarFiles = ArrayList<Path>()
    val dirs = ArrayList<Path>()
    if (!collectPluginDirectoryContents(file, pluginJarFiles, dirs)) {
      return null
    }

    if (!pluginJarFiles.isEmpty()) {
      val pluginPathResolver = PluginXmlPathResolver(pluginJarFiles)
      DescriptorLoadingContext().use { loadingContext ->
        for (jarFile in pluginJarFiles) {
          loadDescriptorFromJar(file = jarFile,
                                fileName = pathName,
                                pathResolver = pluginPathResolver,
                                context = loadingContext,
                                parentContext = parentContext,
                                isBundled = isBundled,
                                isEssential = isEssential,
                                pluginPath = file)?.let {
            it.jarFiles = pluginJarFiles
            return it
          }
        }
      }
    }

    var descriptor: IdeaPluginDescriptorImpl? = null
    for (dir in dirs) {
      val otherDescriptor = loadDescriptorFromDir(file = dir,
                                                  descriptorRelativePath = descriptorRelativePath,
                                                  pluginPath = file,
                                                  context = parentContext,
                                                  isBundled = isBundled,
                                                  isEssential = isEssential,
                                                  pathResolver = pathResolver)
      if (otherDescriptor != null) {
        if (descriptor != null) {
          DescriptorListLoadingContext.LOG.error("Cannot load $file because two or more plugin.xml detected")
          return null
        }
        descriptor = otherDescriptor
      }
    }
    return descriptor
  }

  private fun collectPluginDirectoryContents(file: Path, pluginJarFiles: MutableList<Path>, dirs: MutableList<Path>): Boolean {
    try {
      Files.newDirectoryStream(file.resolve("lib")).use { stream ->
        for (childFile in stream) {
          if (Files.isDirectory(childFile)) {
            dirs.add(childFile)
          }
          else {
            val path = childFile.toString()
            if (path.endsWith(".jar", ignoreCase = true) || path.endsWith(".zip", ignoreCase = true)) {
              pluginJarFiles.add(childFile)
            }
          }
        }
      }
    }
    catch (e: IOException) {
      return false
    }

    if (!pluginJarFiles.isEmpty()) {
      putMoreLikelyPluginJarsFirst(file, pluginJarFiles)
    }
    return true
  }

  /*
   * Sort the files heuristically to load the plugin jar containing plugin descriptors without extra ZipFile accesses
   * File name preference:
   * a) last order for files with resources in name, like resources_en.jar
   * b) last order for files that have -digit suffix is the name e.g. completion-ranking.jar is before gson-2.8.0.jar or junit-m5.jar
   * c) jar with name close to plugin's directory name, e.g. kotlin-XXX.jar is before all-open-XXX.jar
   * d) shorter name, e.g. android.jar is before android-base-common.jar
   */
  private fun putMoreLikelyPluginJarsFirst(pluginDir: Path, filesInLibUnderPluginDir: MutableList<Path>) {
    val pluginDirName = pluginDir.fileName.toString()
    filesInLibUnderPluginDir.sortWith(Comparator { o1: Path, o2: Path ->
      val o2Name = o2.fileName.toString()
      val o1Name = o1.fileName.toString()
      val o2StartsWithResources = o2Name.startsWith("resources")
      val o1StartsWithResources = o1Name.startsWith("resources")
      if (o2StartsWithResources != o1StartsWithResources) {
        return@Comparator if (o2StartsWithResources) -1 else 1
      }

      val o2IsVersioned = fileNameIsLikeVersionedLibraryName(o2Name)
      val o1IsVersioned = fileNameIsLikeVersionedLibraryName(o1Name)
      if (o2IsVersioned != o1IsVersioned) {
        return@Comparator if (o2IsVersioned) -1 else 1
      }

      val o2StartsWithNeededName = o2Name.startsWith(pluginDirName, ignoreCase = true)
      val o1StartsWithNeededName = o1Name.startsWith(pluginDirName, ignoreCase = true)
      if (o2StartsWithNeededName != o1StartsWithNeededName) {
        return@Comparator if (o2StartsWithNeededName) 1 else -1
      }

      val o2EndsWithIdea = o2Name.endsWith("-idea.jar")
      val o1EndsWithIdea = o1Name.endsWith("-idea.jar")
      if (o2EndsWithIdea != o1EndsWithIdea) {
        return@Comparator if (o2EndsWithIdea) 1 else -1
      }
      o1Name.length - o2Name.length
    })
  }

  private fun fileNameIsLikeVersionedLibraryName(name: String): Boolean {
    val i = name.lastIndexOf('-')
    if (i == -1) {
      return false
    }
    if (i + 1 < name.length) {
      val c = name[i + 1]
      if (Character.isDigit(c)) {
        return true
      }
      else {
        return (c == 'm' || c == 'M') && i + 2 < name.length && Character.isDigit(name[i + 2])
      }
    }
    return false
  }

  private fun loadDescriptorsFromProperty(result: PluginLoadingResult, context: DescriptorListLoadingContext) {
    val pathProperty = System.getProperty(PluginManagerCore.PROPERTY_PLUGIN_PATH) ?: return

    // gradle-intellij-plugin heavily depends on this property in order to have core class loader plugins during tests
    val useCoreClassLoaderForPluginsFromProperty = java.lang.Boolean.parseBoolean(
      System.getProperty("idea.use.core.classloader.for.plugin.path"))
    val t = StringTokenizer(pathProperty, File.pathSeparatorChar.toString() + ",")
    while (t.hasMoreTokens()) {
      val s = t.nextToken()
      loadDescriptor(Paths.get(s), false, context)?.let {
        // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
        result.add(it, /* overrideUseIfCompatible = */true)
        if (useCoreClassLoaderForPluginsFromProperty) {
          it.setUseCoreClassLoader()
        }
      }
    }
  }

  @JvmStatic
  fun loadDescriptors(isUnitTestMode: Boolean, isRunningFromSources: Boolean): DescriptorListLoadingContext {
    var flags = DescriptorListLoadingContext.IGNORE_MISSING_SUB_DESCRIPTOR
    if (isUnitTestMode) {
      flags = flags or DescriptorListLoadingContext.IGNORE_MISSING_INCLUDE
    }
    if (isUnitTestMode || isRunningFromSources) {
      flags = flags or DescriptorListLoadingContext.CHECK_OPTIONAL_CONFIG_NAME_UNIQUENESS
    }

    val result = PluginManagerCore.createLoadingResult(null)
    val bundledPluginPath: Path? = if (isUnitTestMode) {
      null
    }
    else if (java.lang.Boolean.getBoolean("idea.use.dev.build.server")) {
      Paths.get(PathManager.getHomePath(), "out/dev-run", PlatformUtils.getPlatformPrefix(), "plugins")
    }
    else {
      Paths.get(PathManager.getPreInstalledPluginsPath())
    }

    val context = DescriptorListLoadingContext(flags, DisabledPluginsState.disabledPlugins(), result)
    context.use {
      loadBundledDescriptorsAndDescriptorsFromDir(context = context,
                                                  customPluginDir = Paths.get(PathManager.getPluginsPath()),
                                                  bundledPluginDir = bundledPluginPath,
                                                  isUnitTestMode = isUnitTestMode,
                                                  isRunningFromSources = isRunningFromSources)
      loadDescriptorsFromProperty(result, context)
      if (isUnitTestMode && result.enabledPluginCount() <= 1) {
        // we're running in unit test mode, but the classpath doesn't contain any plugins; try to load bundled plugins anyway
        context.usePluginClassLoader = true
        ForkJoinPool.commonPool().invoke(LoadDescriptorsFromDirAction(Paths.get(PathManager.getPreInstalledPluginsPath()), context, true))
      }
    }
    context.result.finishLoading()
    return context
  }

  @JvmStatic
  fun loadBundledDescriptorsAndDescriptorsFromDir(context: DescriptorListLoadingContext,
                                                  customPluginDir: Path,
                                                  bundledPluginDir: Path?,
                                                  isUnitTestMode: Boolean,
                                                  isRunningFromSources: Boolean) {
    val classLoader = PluginDescriptorLoader::class.java.classLoader
    val pool = ForkJoinPool.commonPool()
    var activity = StartUpMeasurer.startActivity("platform plugin collecting", ActivityCategory.DEFAULT)

    val platformPrefix = PlatformUtils.getPlatformPrefix()
    // should be the only plugin in lib (only for Ultimate and WebStorm for now)
    val pathResolver = ClassPathXmlPathResolver(classLoader)
    if ((platformPrefix == PlatformUtils.IDEA_PREFIX || platformPrefix == PlatformUtils.WEB_PREFIX) &&
        (java.lang.Boolean.getBoolean("idea.use.dev.build.server") || (!isUnitTestMode && !isRunningFromSources))) {
      val factory = context.xmlFactory
      val element = JDOMUtil.load(classLoader.getResourceAsStream(PluginManagerCore.PLUGIN_XML_PATH)!!, factory)

      val descriptor = IdeaPluginDescriptorImpl(Paths.get(PathManager.getLibPath()), true)
      descriptor.readExternal(element, pathResolver, context, descriptor, object : DataLoader {
        override val pool: ZipFilePool
          get() = throw IllegalStateException("must be not called")

        override fun load(path: String) = throw IllegalStateException("must be not called")

        override fun toString() = "product classpath"
      })
      descriptor.setUseCoreClassLoader()
      context.result.add(descriptor, /* overrideUseIfCompatible = */false)
    }
    else {
      val urlsFromClassPath = LinkedHashMap<URL, String>()
      val platformPluginURL = computePlatformPluginUrlAndCollectPluginUrls(classLoader, urlsFromClassPath, platformPrefix)
      if (!urlsFromClassPath.isEmpty()) {
        activity = activity.endAndStart("plugin from classpath loading")
        pool.invoke(LoadDescriptorsFromClassPathAction(urls = urlsFromClassPath,
                                                       context = context,
                                                       platformPluginURL = platformPluginURL,
                                                       pathResolver = pathResolver))
      }
    }

    activity = activity.endAndStart("plugin from user dir loading")
    pool.invoke(LoadDescriptorsFromDirAction(customPluginDir, context, false))
    if (bundledPluginDir != null) {
      activity = activity.endAndStart("plugin from bundled dir loading")
      pool.invoke(LoadDescriptorsFromDirAction(bundledPluginDir, context, true))
    }
    activity.end()
  }

  private fun computePlatformPluginUrlAndCollectPluginUrls(loader: ClassLoader,
                                                           urls: MutableMap<URL, String>,
                                                           platformPrefix: String?): URL? {
    var result: URL? = null
    if (platformPrefix != null) {
      val fileName = "${platformPrefix}Plugin.xml"
      loader.getResource("${PluginManagerCore.META_INF}$fileName")?.let {
        urls.put(it, fileName)
        result = it
      }
    }
    collectPluginFilesInClassPath(loader, urls)
    return result
  }

  private fun collectPluginFilesInClassPath(loader: ClassLoader, urls: MutableMap<URL, String>) {
    try {
      val enumeration = loader.getResources(PluginManagerCore.PLUGIN_XML_PATH)
      while (enumeration.hasMoreElements()) {
        urls.put(enumeration.nextElement(), PluginManagerCore.PLUGIN_XML)
      }
    }
    catch (e: IOException) {
      DescriptorListLoadingContext.LOG.warn(e)
    }
  }

  /**
   * Think twice before use and get approve from core team.
   *
   * Returns enabled plugins only.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun loadUncachedDescriptors(isUnitTestMode: Boolean, isRunningFromSources: Boolean): List<IdeaPluginDescriptorImpl> {
    return loadDescriptors(isUnitTestMode = isUnitTestMode, isRunningFromSources = isRunningFromSources).result.enabledPlugins
  }

  @Throws(IOException::class)
  @JvmStatic
  fun loadDescriptorFromArtifact(file: Path, buildNumber: BuildNumber?): IdeaPluginDescriptorImpl? {
    val context = DescriptorListLoadingContext(DescriptorListLoadingContext.IGNORE_MISSING_SUB_DESCRIPTOR,
                                               DisabledPluginsState.disabledPlugins(),
                                               PluginManagerCore.createLoadingResult(buildNumber))
    var outputDir: Path? = null
    try {
      var descriptor = loadDescriptorFromFileOrDir(file = file,
                                                   pathName = PluginManagerCore.PLUGIN_XML,
                                                   context = context,
                                                   pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                                   isBundled = false,
                                                   isEssential = false,
                                                   isDirectory = false)
      if (descriptor != null || !file.toString().endsWith(".zip")) {
        return descriptor
      }

      outputDir = Files.createTempDirectory("plugin")
      Decompressor.Zip(file).extract(outputDir!!)
      try {
        Files.newDirectoryStream(outputDir).use { stream ->
          val iterator = stream.iterator()
          if (iterator.hasNext()) {
            descriptor = loadDescriptorFromFileOrDir(file = iterator.next(),
                                                     pathName = PluginManagerCore.PLUGIN_XML,
                                                     context = context,
                                                     pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                                     isBundled = false,
                                                     isEssential = false,
                                                     isDirectory = true)
          }
        }
      }
      catch (ignore: NoSuchFileException) {
      }
      return descriptor
    }
    finally {
      outputDir?.let {
        FileUtil.delete(it)
      }
    }
  }

  @JvmStatic
  fun tryLoadFullDescriptor(descriptor: IdeaPluginDescriptorImpl): IdeaPluginDescriptorImpl? {
    if (!PluginManagerCore.hasDescriptorByIdentity(descriptor) || PluginManagerCore.getLoadedPlugins().contains(descriptor)) {
      return descriptor
    }
    else {
      return loadDescriptor(file = descriptor.pluginPath,
                            disabledPlugins = emptySet(),
                            isBundled = descriptor.isBundled,
                            pathResolver = createPathResolverForPlugin(descriptor = descriptor, checkPluginJarFiles = false))
    }
  }

  @JvmStatic
  fun loadDescriptor(file: Path,
                     disabledPlugins: Set<PluginId?>,
                     isBundled: Boolean,
                     pathResolver: PathResolver): IdeaPluginDescriptorImpl? {
    DescriptorListLoadingContext.createSingleDescriptorContext(disabledPlugins).use { context ->
      return loadDescriptorFromFileOrDir(file = file,
                                         pathName = PluginManagerCore.PLUGIN_XML,
                                         context = context,
                                         pathResolver = pathResolver,
                                         isBundled = isBundled,
                                         isEssential = false,
                                         isDirectory = Files.isDirectory(file))
    }
  }

  fun createPathResolverForPlugin(descriptor: IdeaPluginDescriptorImpl, checkPluginJarFiles: Boolean): PathResolver {
    if (PluginManagerCore.isRunningFromSources() && descriptor.pluginPath.fileSystem == FileSystems.getDefault() &&
        descriptor.pluginPath.toString().contains("out/classes")) {
      return ClassPathXmlPathResolver(descriptor.pluginClassLoader)
    }
    else if (checkPluginJarFiles) {
      val pluginJarFiles = ArrayList<Path>()
      val dirs = ArrayList<Path>()
      if (collectPluginDirectoryContents(descriptor.pluginPath, pluginJarFiles, dirs)) {
        return PluginXmlPathResolver(pluginJarFiles)
      }
    }
    return PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  }

  @JvmStatic
  fun loadFullDescriptor(descriptor: IdeaPluginDescriptorImpl): IdeaPluginDescriptorImpl {
    // PluginDescriptor fields are cleaned after the plugin is loaded, so we need to reload the descriptor to check if it's dynamic
    val fullDescriptor = tryLoadFullDescriptor(descriptor)
    if (fullDescriptor == null) {
      DescriptorListLoadingContext.LOG.error("Could not load full descriptor for plugin ${descriptor.pluginPath}")
      return descriptor
    }
    else {
      return fullDescriptor
    }
  }

  @TestOnly
  @JvmStatic
  fun testLoadDescriptorsFromClassPath(loader: ClassLoader): List<IdeaPluginDescriptor> {
    val urlsFromClassPath = LinkedHashMap<URL, String>()
    collectPluginFilesInClassPath(loader, urlsFromClassPath)
    val buildNumber = BuildNumber.fromString("2042.42")
    val context = DescriptorListLoadingContext(0, emptySet(), PluginLoadingResult(emptyMap(), Supplier { buildNumber }, false))
    LoadDescriptorsFromClassPathAction(urlsFromClassPath, context, null, ClassPathXmlPathResolver(loader)).compute()
    context.result.finishLoading()
    return context.result.enabledPlugins
  }
}

private class LoadDescriptorsFromDirAction(private val dir: Path,
                                           private val context: DescriptorListLoadingContext,
                                           private val isBundled: Boolean) : RecursiveAction() {
  override fun compute() {
    val tasks = ArrayList<RecursiveTask<IdeaPluginDescriptorImpl?>>()
    try {
      Files.newDirectoryStream(dir).use { dirStream ->
        for (file in dirStream) {
          tasks.add(object : RecursiveTask<IdeaPluginDescriptorImpl?>() {
            override fun compute(): IdeaPluginDescriptorImpl? {
              if (Files.isDirectory(file)) {
                return PluginDescriptorLoader.loadDescriptorFromDirAndNormalize(file = file,
                                                                                pathName = PluginManagerCore.PLUGIN_XML,
                                                                                parentContext = context,
                                                                                isBundled = isBundled,
                                                                                isEssential = false,
                                                                                pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER)
              }
              else if (file.fileName.toString().endsWith(".jar", ignoreCase = true)) {
                DescriptorLoadingContext().use { loadingContext ->
                  return PluginDescriptorLoader.loadDescriptorFromJar(file = file,
                                                                      fileName = PluginManagerCore.PLUGIN_XML,
                                                                      pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                                                      context = loadingContext,
                                                                      parentContext = context,
                                                                      isBundled = isBundled,
                                                                      isEssential = false,
                                                                      pluginPath = null)
                }
              }
              else {
                return null
              }
            }
          })
        }
      }
    }
    catch (ignore: IOException) {
      return
    }

    ForkJoinTask.invokeAll(tasks)
    for (task in tasks) {
      task.rawResult?.let {
        context.result.add(it,  /* overrideUseIfCompatible = */false)
      }
    }
  }
}

private class LoadDescriptorsFromClassPathAction(private val urls: Map<URL, String>,
                                                 private val context: DescriptorListLoadingContext,
                                                 private val platformPluginURL: URL?,
                                                 private val pathResolver: PathResolver) : RecursiveAction() {
  public override fun compute() {
    val tasks = ArrayList<RecursiveTask<IdeaPluginDescriptorImpl?>>(urls.size)
    for ((url, value) in urls) {
      tasks.add(object : RecursiveTask<IdeaPluginDescriptorImpl?>() {
        override fun compute(): IdeaPluginDescriptorImpl? {
          val isEssential = url == platformPluginURL
          try {
            return loadDescriptorFromResource(resource = url, pathName = value, isEssential = isEssential)
          }
          catch (e: Throwable) {
            if (isEssential) {
              throw e
            }
            DescriptorListLoadingContext.LOG.info("Cannot load $url", e)
            return null
          }
        }
      })
    }

    val result = context.result
    ForkJoinTask.invokeAll(tasks)
    val usePluginClassLoader = PluginManagerCore.usePluginClassLoader
    for (task in tasks) {
      task.rawResult?.let {
        if (!usePluginClassLoader) {
          it.setUseCoreClassLoader()
        }
        result.add(it, /* overrideUseIfCompatible = */false)
      }
    }
  }

  private fun loadDescriptorFromResource(resource: URL, pathName: String, isEssential: Boolean): IdeaPluginDescriptorImpl? {
    when {
      URLUtil.FILE_PROTOCOL == resource.protocol -> {
        val file = Paths.get(Strings.trimEnd(UrlClassLoader.urlToFilePath(resource.path).replace('\\', '/'), pathName)).parent
        return PluginDescriptorLoader.loadDescriptorFromFileOrDir(file = file,
                                                                  pathName = pathName,
                                                                  context = context,
                                                                  pathResolver = pathResolver,
                                                                  isBundled = true,
                                                                  isEssential = isEssential,
                                                                  isDirectory = Files.isDirectory(file))
      }
      URLUtil.JAR_PROTOCOL == resource.protocol -> {
        val file = Paths.get(UrlClassLoader.urlToFilePath(resource.path))
        val parentFile = file.parent
        if (parentFile == null || !parentFile.endsWith("lib")) {
          DescriptorLoadingContext().use { loadingContext ->
            return PluginDescriptorLoader.loadDescriptorFromJar(file = file,
                                                                fileName = pathName,
                                                                pathResolver = pathResolver,
                                                                context = loadingContext,
                                                                parentContext = context,
                                                                isBundled = true,
                                                                isEssential = isEssential,
                                                                pluginPath = null)
          }
        }
        else {
          // Support for unpacked plugins in classpath. E.g. .../out/artifacts/KotlinPlugin/lib/kotlin-plugin.jar
          DescriptorLoadingContext().use { loadingContext ->
            val descriptor = PluginDescriptorLoader.loadDescriptorFromJar(file = file,
                                                                          fileName = pathName,
                                                                          pathResolver = pathResolver,
                                                                          context = loadingContext,
                                                                          parentContext = context,
                                                                          isBundled = true,
                                                                          isEssential = isEssential,
                                                                          pluginPath = file.parent.parent)

            if (descriptor != null) {
              descriptor.jarFiles = null
            }
            return descriptor
          }
        }
      }
      else -> {
        return null
      }
    }
  }
}