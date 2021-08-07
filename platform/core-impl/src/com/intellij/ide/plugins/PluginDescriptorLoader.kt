// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
@file:JvmName("PluginDescriptorLoader")
@file:ApiStatus.Internal
package com.intellij.ide.plugins

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.util.plugins.DataLoader
import com.intellij.platform.util.plugins.LocalFsDataLoader
import com.intellij.util.PlatformUtils
import com.intellij.util.io.Decompressor
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.*
import java.util.function.Supplier
import java.util.zip.ZipFile
import javax.xml.stream.XMLStreamException

private val LOG: Logger
  get() = PluginManagerCore.getLogger()

internal fun createPluginLoadingResult(buildNumber: BuildNumber?): PluginLoadingResult {
  return PluginLoadingResult(brokenPluginVersions = PluginManagerCore.getBrokenPluginVersions(),
                             productBuildNumber = { buildNumber ?: PluginManagerCore.getBuildNumber() })
}

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
  val parentContext = DescriptorListLoadingContext(disabledPlugins = DisabledPluginsState.disabledPlugins())
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
    return loadDescriptorFromJar(file = pluginRoot,
                                 fileName = fileName,
                                 pathResolver = pathResolver,
                                 parentContext = parentContext,
                                 isBundled = true,
                                 isEssential = true,
                                 pluginPath = null)
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
    val input = Files.readAllBytes(file.resolve(descriptorRelativePath))
    val dataLoader = LocalFsDataLoader(file)
    val raw = readModuleDescriptor(input = input,
                                   readContext = context,
                                   pathResolver = pathResolver,
                                   dataLoader = dataLoader,
                                   includeBase = null,
                                   readInto = null,
                                   locationSource = file.toString())
    val descriptor = IdeaPluginDescriptorImpl(raw = raw, path = pluginPath ?: file, isBundled = isBundled, id = null)
    descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = context, isSub = false, dataLoader = dataLoader)
    descriptor.jarFiles = Collections.singletonList(file)
    return descriptor
  }
  catch (e: NoSuchFileException) {
    return null
  }
  catch (e: Throwable) {
    if (isEssential) {
      throw e
    }
    LOG.warn("Cannot load ${file.resolve(descriptorRelativePath)}", e)
    return null
  }
}

private fun loadDescriptorFromJar(file: Path,
                                  fileName: String,
                                  pathResolver: PathResolver,
                                  parentContext: DescriptorListLoadingContext,
                                  isBundled: Boolean,
                                  isEssential: Boolean,
                                  pluginPath: Path?): IdeaPluginDescriptorImpl? {
  var closeable: Closeable? = null
  try {
    val dataLoader: DataLoader
    val pool = ZipFilePool.POOL
    if (pool == null || parentContext.transient) {
      val zipFile = ZipFile(file.toFile(), StandardCharsets.UTF_8)
      closeable = zipFile
      dataLoader = JavaZipFileDataLoader(zipFile)
    }
    else {
      dataLoader = ImmutableZipFileDataLoader(pool.load(file), file, pool)
    }

    val raw = readModuleDescriptor(input = dataLoader.load("META-INF/$fileName") ?: return null,
                                   readContext = parentContext,
                                   pathResolver = pathResolver,
                                   dataLoader = dataLoader,
                                   includeBase = null,
                                   readInto = null,
                                   locationSource = file.toString())

    val descriptor = IdeaPluginDescriptorImpl(raw = raw, path = pluginPath ?: file, isBundled = isBundled, id = null)
    descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = parentContext, isSub = false, dataLoader = dataLoader)
    descriptor.jarFiles = Collections.singletonList(descriptor.pluginPath)
    return descriptor
  }
  catch (e: Throwable) {
    if (isEssential) {
      throw if (e is XMLStreamException) RuntimeException("Cannot read $file", e) else e
    }
    parentContext.result.reportCannotLoad(file, e)
  }
  finally {
    closeable?.close()
  }
  return null
}

private class JavaZipFileDataLoader(private val file: ZipFile) : DataLoader {
  override val pool: ZipFilePool?
    get() = null

  override fun load(path: String): ByteArray? {
    val entry = file.getEntry(if (path[0] == '/') path.substring(1) else path) ?: return null
    return file.getInputStream(entry).use { it.readBytes() }
  }

  override fun toString() = file.toString()
}

fun loadDescriptorFromFileOrDir(file: Path,
                                pathName: String,
                                context: DescriptorListLoadingContext,
                                pathResolver: PathResolver,
                                isBundled: Boolean,
                                isEssential: Boolean,
                                isDirectory: Boolean): IdeaPluginDescriptorImpl? {
  return when {
    isDirectory -> {
      val descriptorRelativePath = "${PluginManagerCore.META_INF}$pathName"
      loadDescriptorFromDir(file = file,
                            descriptorRelativePath = descriptorRelativePath,
                            pluginPath = null,
                            context = context,
                            isBundled = isBundled,
                            isEssential = isEssential,
                            pathResolver = pathResolver)?.let {
        return it
      }
      loadFromPluginDir(file = file, pathName = pathName, parentContext = context, isBundled = isBundled,
                        isEssential = isEssential, descriptorRelativePath = descriptorRelativePath, pathResolver = pathResolver)
    }
    file.fileName.toString().endsWith(".jar", ignoreCase = true) -> {
      loadDescriptorFromJar(file = file,
                            fileName = pathName,
                            pathResolver = pathResolver,
                            parentContext = context,
                            isBundled = isBundled,
                            isEssential = isEssential,
                            pluginPath = null)
    }
    else -> null
  }
}

// classes + lib/*.jar
// or
// lib/*.jar
private fun loadFromPluginDir(file: Path,
                              pathName: String,
                              parentContext: DescriptorListLoadingContext,
                              isBundled: Boolean,
                              isEssential: Boolean,
                              descriptorRelativePath: String,
                              pathResolver: PathResolver): IdeaPluginDescriptorImpl? {
  val pluginJarFiles = ArrayList<Path>()
  try {
    Files.newDirectoryStream(file.resolve("lib")).use { stream ->
      for (childFile in stream) {
        val path = childFile.toString()
        if (path.endsWith(".jar", ignoreCase = true) || path.endsWith(".zip", ignoreCase = true)) {
          pluginJarFiles.add(childFile)
        }
      }
    }
  }
  catch (e: NoSuchFileException) {
    return null
  }

  if (!pluginJarFiles.isEmpty()) {
    putMoreLikelyPluginJarsFirst(file, pluginJarFiles)
    val pluginPathResolver = PluginXmlPathResolver(pluginJarFiles)
    for (jarFile in pluginJarFiles) {
      loadDescriptorFromJar(file = jarFile,
                            fileName = pathName,
                            pathResolver = pluginPathResolver,
                            parentContext = parentContext,
                            isBundled = isBundled,
                            isEssential = isEssential,
                            pluginPath = file)?.let {
        it.jarFiles = pluginJarFiles
        return it
      }
    }
  }

  // not found, ok, let's check classes (but only for unbundled plugins)
  if (!isBundled) {
    val classesDir = file.resolve("classes")
    loadDescriptorFromDir(file = classesDir,
                          descriptorRelativePath = descriptorRelativePath,
                          pluginPath = file,
                          context = parentContext,
                          isBundled = isBundled,
                          isEssential = isEssential,
                          pathResolver = pathResolver)?.let {
      val classPath = ArrayList<Path>(pluginJarFiles.size + 1)
      classPath.add(classesDir)
      classPath.addAll(pluginJarFiles)
      it.jarFiles = classPath
      return it
    }
  }
  return null
}

/*
 * Sort the files heuristically to load the plugin jar containing plugin descriptors without extra ZipFile accesses.
 * File name preference:
 * a) last order for files with resources in name, like resources_en.jar
 * b) last order for files that have `-digit` suffix is the name e.g., completion-ranking.jar is before `gson-2.8.0.jar` or `junit-m5.jar`
 * c) jar with name close to plugin's directory name, e.g., kotlin-XXX.jar is before all-open-XXX.jar
 * d) shorter name, e.g., android.jar is before android-base-common.jar
 */
private fun putMoreLikelyPluginJarsFirst(pluginDir: Path, filesInLibUnderPluginDir: MutableList<Path>) {
  val pluginDirName = pluginDir.fileName.toString()
  // don't use kotlin sortWith to avoid loading of CollectionsKt
  Collections.sort(filesInLibUnderPluginDir, Comparator { o1: Path, o2: Path ->
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
    return Character.isDigit(c) || ((c == 'm' || c == 'M') && i + 2 < name.length && Character.isDigit(name[i + 2]))
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

internal fun loadDescriptors(isUnitTestMode: Boolean, isRunningFromSources: Boolean): DescriptorListLoadingContext {
  val result = createPluginLoadingResult(null)
  val bundledPluginPath: Path? = if (isUnitTestMode) {
    null
  }
  else if (java.lang.Boolean.getBoolean("idea.use.dev.build.server")) {
    Paths.get(PathManager.getHomePath(), "out/dev-run", PlatformUtils.getPlatformPrefix(), "plugins")
  }
  else {
    Paths.get(PathManager.getPreInstalledPluginsPath())
  }

  val context = DescriptorListLoadingContext(isMissingSubDescriptorIgnored = true,
                                             isMissingIncludeIgnored = isUnitTestMode,
                                             checkOptionalConfigFileUniqueness = isUnitTestMode || isRunningFromSources,
                                             disabledPlugins = DisabledPluginsState.disabledPlugins(),
                                             result = result)
  context.use {
    loadBundledDescriptorsAndDescriptorsFromDir(context = context,
                                                customPluginDir = Paths.get(PathManager.getPluginsPath()),
                                                bundledPluginDir = bundledPluginPath,
                                                isUnitTestMode = isUnitTestMode,
                                                isRunningFromSources = isRunningFromSources)
    loadDescriptorsFromProperty(result, context)
    if (isUnitTestMode && result.enabledPluginCount() <= 1) {
      // we're running in unit test mode, but the classpath doesn't contain any plugins; try to load bundled plugins anyway
      ForkJoinPool.commonPool().invoke(LoadDescriptorsFromDirAction(Paths.get(PathManager.getPreInstalledPluginsPath()), context,
                                                                    isBundled = true))
    }
  }
  context.result.finishLoading()
  return context
}

private fun loadBundledDescriptorsAndDescriptorsFromDir(context: DescriptorListLoadingContext,
                                                        customPluginDir: Path,
                                                        bundledPluginDir: Path?,
                                                        isUnitTestMode: Boolean,
                                                        isRunningFromSources: Boolean) {
  val classLoader = DescriptorListLoadingContext::class.java.classLoader
  val pool = ForkJoinPool.commonPool()
  var activity = StartUpMeasurer.startActivity("platform plugin collecting", ActivityCategory.DEFAULT)

  val platformPrefix = PlatformUtils.getPlatformPrefix()
  // should be the only plugin in lib (only for Ultimate and WebStorm for now)
  val pathResolver = ClassPathXmlPathResolver(classLoader, isRunningFromSources = isRunningFromSources)
  if ((platformPrefix == PlatformUtils.IDEA_PREFIX || platformPrefix == PlatformUtils.WEB_PREFIX) &&
      (java.lang.Boolean.getBoolean("idea.use.dev.build.server") || (!isUnitTestMode && !isRunningFromSources))) {
    val dataLoader = object : DataLoader {
      override val pool: ZipFilePool
        get() = throw IllegalStateException("must be not called")

      override fun load(path: String) = throw IllegalStateException("must be not called")

      override fun toString() = "product classpath"
    }

    val raw = readModuleDescriptor(inputStream = classLoader.getResourceAsStream(PluginManagerCore.PLUGIN_XML_PATH)!!,
                                   readContext = context,
                                   pathResolver = pathResolver,
                                   dataLoader = dataLoader,
                                   includeBase = null,
                                   readInto = null,
                                   locationSource = null)
    val descriptor = IdeaPluginDescriptorImpl(raw = raw, path = Paths.get(PathManager.getLibPath()), isBundled = true, id = null)
    descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = context, isSub = false, dataLoader = dataLoader)
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
  pool.invoke(LoadDescriptorsFromDirAction(customPluginDir, context, isBundled = false))
  if (bundledPluginDir != null) {
    activity = activity.endAndStart("plugin from bundled dir loading")
    pool.invoke(LoadDescriptorsFromDirAction(bundledPluginDir, context, isBundled = true))
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
    LOG.warn(e)
  }
}

/**
 * Think twice before use and get approve from core team.
 *
 * Returns enabled plugins only.
 */
fun loadUncachedDescriptors(isUnitTestMode: Boolean, isRunningFromSources: Boolean): List<IdeaPluginDescriptorImpl> {
  return loadDescriptors(isUnitTestMode = isUnitTestMode, isRunningFromSources = isRunningFromSources).result.getEnabledPlugins()
}

@Throws(IOException::class)
fun loadDescriptorFromArtifact(file: Path, buildNumber: BuildNumber?): IdeaPluginDescriptorImpl? {
  val context = DescriptorListLoadingContext(isMissingSubDescriptorIgnored = true,
                                             disabledPlugins = DisabledPluginsState.disabledPlugins(),
                                             result = createPluginLoadingResult(buildNumber),
                                             transient = true)

  val descriptor = loadDescriptorFromFileOrDir(file = file,
                                               pathName = PluginManagerCore.PLUGIN_XML,
                                               context = context,
                                               pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                               isBundled = false,
                                               isEssential = false,
                                               isDirectory = false)
  if (descriptor != null || !file.toString().endsWith(".zip")) {
    return descriptor
  }

  val outputDir = Files.createTempDirectory("plugin")!!
  try {
    Decompressor.Zip(file).extract(outputDir)
    try {
      val rootDir = NioFiles.list(outputDir).firstOrNull()
      if (rootDir != null) {
        return loadDescriptorFromFileOrDir(file = rootDir,
                                           pathName = PluginManagerCore.PLUGIN_XML,
                                           context = context,
                                           pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                           isBundled = false,
                                           isEssential = false,
                                           isDirectory = true)
      }
    }
    catch (ignore: NoSuchFileException) { }
  }
  finally {
    NioFiles.deleteRecursively(outputDir)
  }

  return null
}

fun loadDescriptor(file: Path,
                   disabledPlugins: Set<PluginId>,
                   isBundled: Boolean,
                   pathResolver: PathResolver): IdeaPluginDescriptorImpl? {
  DescriptorListLoadingContext(disabledPlugins = disabledPlugins).use { context ->
    return loadDescriptorFromFileOrDir(file = file,
                                       pathName = PluginManagerCore.PLUGIN_XML,
                                       context = context,
                                       pathResolver = pathResolver,
                                       isBundled = isBundled,
                                       isEssential = false,
                                       isDirectory = Files.isDirectory(file))
  }
}

@Throws(ExecutionException::class, InterruptedException::class)
fun getDescriptorsToMigrate(dir: Path,
                            compatibleBuildNumber: BuildNumber?,
                            bundledPluginsPath: Path?,
                            brokenPluginVersions: Map<PluginId, Set<String>>?,
                            pluginsToMigrate: MutableList<IdeaPluginDescriptorImpl?>,
                            incompatiblePlugins: MutableList<IdeaPluginDescriptorImpl?>) {
  val loadingResult = PluginLoadingResult(brokenPluginVersions = brokenPluginVersions ?: PluginManagerCore.getBrokenPluginVersions(),
                                          productBuildNumber = Supplier { compatibleBuildNumber ?: PluginManagerCore.getBuildNumber() }
  )
  val context = DescriptorListLoadingContext(disabledPlugins = emptySet(),
                                             result = loadingResult,
                                             isMissingIncludeIgnored = true,
                                             isMissingSubDescriptorIgnored = true)
  val effectiveBundledPluginPath = if (bundledPluginsPath != null || PluginManagerCore.isUnitTestMode) {
    bundledPluginsPath
  }
  else {
    Paths.get(PathManager.getPreInstalledPluginsPath())
  }
  loadBundledDescriptorsAndDescriptorsFromDir(context = context,
                                              customPluginDir = dir,
                                              bundledPluginDir = effectiveBundledPluginPath,
                                              isUnitTestMode = PluginManagerCore.isUnitTestMode,
                                              isRunningFromSources = PluginManagerCore.isRunningFromSources())
  for (descriptor in loadingResult.idMap.values) {
    if (!descriptor.isBundled) {
      if (loadingResult.isBroken(descriptor.pluginId)) {
        incompatiblePlugins.add(descriptor)
      }
      else {
        pluginsToMigrate.add(descriptor)
      }
    }
  }
  for (descriptor in loadingResult.incompletePlugins.values) {
    if (!descriptor.isBundled) {
      incompatiblePlugins.add(descriptor)
    }
  }
}

@TestOnly
fun testLoadDescriptorsFromClassPath(loader: ClassLoader): List<IdeaPluginDescriptor> {
  val urlsFromClassPath = LinkedHashMap<URL, String>()
  collectPluginFilesInClassPath(loader, urlsFromClassPath)
  val buildNumber = BuildNumber.fromString("2042.42")!!
  val context = DescriptorListLoadingContext(disabledPlugins = emptySet(),
                                             result = PluginLoadingResult(brokenPluginVersions = emptyMap(),
                                                                          productBuildNumber = Supplier { buildNumber },
                                                                          checkModuleDependencies = false))
  LoadDescriptorsFromClassPathAction(urlsFromClassPath, context, null, ClassPathXmlPathResolver(loader, isRunningFromSources = false)).compute()
  context.result.finishLoading()
  return context.result.getEnabledPlugins()
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
                return loadFromPluginDir(file = file,
                                         pathName = PluginManagerCore.PLUGIN_XML,
                                         parentContext = context,
                                         isBundled = isBundled,
                                         isEssential = false,
                                         pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                         descriptorRelativePath = "${PluginManagerCore.META_INF}${PluginManagerCore.PLUGIN_XML}")
              }
              else if (file.fileName.toString().endsWith(".jar", ignoreCase = true)) {
                return loadDescriptorFromJar(file = file,
                                             fileName = PluginManagerCore.PLUGIN_XML,
                                             pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                             parentContext = context,
                                             isBundled = isBundled,
                                             isEssential = false,
                                             pluginPath = null)
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
            LOG.info("Cannot load $url", e)
            return null
          }
        }
      })
    }

    val result = context.result
    ForkJoinTask.invokeAll(tasks)
    for (task in tasks) {
      task.rawResult?.let {
        it.setUseCoreClassLoader()
        result.add(it, overrideUseIfCompatible = false)
      }
    }
  }

  private fun loadDescriptorFromResource(resource: URL, pathName: String, isEssential: Boolean): IdeaPluginDescriptorImpl? {
    when {
      URLUtil.FILE_PROTOCOL == resource.protocol -> {
        val file = Paths.get(Strings.trimEnd(UrlClassLoader.urlToFilePath(resource.path).replace('\\', '/'), pathName)).parent
        return loadDescriptorFromFileOrDir(file = file,
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
          return loadDescriptorFromJar(file = file,
                                       fileName = pathName,
                                       pathResolver = pathResolver,
                                       parentContext = context,
                                       isBundled = true,
                                       isEssential = isEssential,
                                       pluginPath = null)
        }
        else {
          // Support for unpacked plugins in classpath. E.g. .../community/build/dependencies/build/kotlin/Kotlin/lib/kotlin-plugin.jar
          val descriptor = loadDescriptorFromJar(file = file,
                                                 fileName = pathName,
                                                 pathResolver = pathResolver,
                                                 parentContext = context,
                                                 isBundled = true,
                                                 isEssential = isEssential,
                                                 pluginPath = file.parent.parent)
          if (descriptor != null) {
            descriptor.jarFiles = Collections.singletonList(file)
          }
          return descriptor
        }
      }
      else -> {
        return null
      }
    }
  }
}
