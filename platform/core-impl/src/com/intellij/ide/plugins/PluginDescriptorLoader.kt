// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty", "ReplacePutWithAssignment")
@file:JvmName("PluginDescriptorLoader")
@file:ApiStatus.Internal
package com.intellij.ide.plugins

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.createNonCoalescingXmlStreamReader
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.PlatformUtils
import com.intellij.util.io.Decompressor
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.*
import java.util.function.BiConsumer
import java.util.function.Supplier
import java.util.zip.ZipFile
import javax.xml.stream.XMLStreamException

private val LOG: Logger
  get() = PluginManagerCore.getLogger()

internal fun createPluginLoadingResult(buildNumber: BuildNumber?): PluginLoadingResult {
  return PluginLoadingResult(brokenPluginVersions = PluginManagerCore.getBrokenPluginVersions(),
                             productBuildNumber = { buildNumber ?: PluginManagerCore.getBuildNumber() })
}

@TestOnly
fun loadDescriptor(file: Path, parentContext: DescriptorListLoadingContext): IdeaPluginDescriptorImpl? {
  return loadDescriptorFromFileOrDir(file = file,
                                     context = parentContext,
                                     pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                     isBundled = false,
                                     isEssential = false,
                                     isDirectory = Files.isDirectory(file),
                                     useCoreClassLoader = false)
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
                                 pathResolver = pathResolver,
                                 useCoreClassLoader = false)
  }
  else {
    return loadDescriptorFromJar(file = pluginRoot,
                                 fileName = fileName,
                                 pathResolver = pathResolver,
                                 parentContext = parentContext,
                                 isBundled = true,
                                 isEssential = true,
                                 pluginPath = null,
                                 useCoreClassLoader = false)
  }
}

private fun loadDescriptorFromDir(file: Path,
                                  descriptorRelativePath: String,
                                  pluginPath: Path?,
                                  context: DescriptorListLoadingContext,
                                  isBundled: Boolean,
                                  isEssential: Boolean,
                                  useCoreClassLoader: Boolean,
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
    val descriptor = IdeaPluginDescriptorImpl(raw = raw, path = pluginPath ?: file, isBundled = isBundled, id = null, moduleName = null,
                                              useCoreClassLoader = useCoreClassLoader)
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
                                  useCoreClassLoader: Boolean,
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

    val descriptor = IdeaPluginDescriptorImpl(raw = raw, path = pluginPath ?: file, isBundled = isBundled, id = null, moduleName = null,
                                              useCoreClassLoader = useCoreClassLoader)
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

  override fun load(path: String): InputStream? {
    val entry = file.getEntry(if (path[0] == '/') path.substring(1) else path) ?: return null
    return file.getInputStream(entry)
  }

  override fun toString() = file.toString()
}

fun loadDescriptorFromFileOrDir(
  file: Path,
  context: DescriptorListLoadingContext,
  pathResolver: PathResolver,
  isBundled: Boolean,
  isEssential: Boolean,
  isDirectory: Boolean,
  useCoreClassLoader: Boolean,
  isUnitTestMode: Boolean = false
): IdeaPluginDescriptorImpl? {
  return when {
    isDirectory -> {
      loadFromPluginDir(
        file = file,
        parentContext = context,
        isBundled = isBundled,
        isEssential = isEssential,
        useCoreClassLoader = useCoreClassLoader,
        pathResolver = pathResolver,
        isUnitTestMode = isUnitTestMode,
      )
    }
    file.fileName.toString().endsWith(".jar", ignoreCase = true) -> {
      loadDescriptorFromJar(file = file,
                            fileName = PluginManagerCore.PLUGIN_XML,
                            pathResolver = pathResolver,
                            parentContext = context,
                            isBundled = isBundled,
                            isEssential = isEssential,
                            pluginPath = null,
                            useCoreClassLoader = useCoreClassLoader)
    }
    else -> null
  }
}

// [META-INF] [classes] lib/*.jar
private fun loadFromPluginDir(
  file: Path,
  parentContext: DescriptorListLoadingContext,
  isBundled: Boolean,
  isEssential: Boolean,
  useCoreClassLoader: Boolean,
  pathResolver: PathResolver,
  isUnitTestMode: Boolean = false,
): IdeaPluginDescriptorImpl? {
  val pluginJarFiles = ArrayList(resolveArchives(file))

  if (!pluginJarFiles.isEmpty()) {
    putMoreLikelyPluginJarsFirst(file, pluginJarFiles)
    val pluginPathResolver = PluginXmlPathResolver(pluginJarFiles)
    for (jarFile in pluginJarFiles) {
      loadDescriptorFromJar(file = jarFile,
                            fileName = PluginManagerCore.PLUGIN_XML,
                            pathResolver = pluginPathResolver,
                            parentContext = parentContext,
                            isBundled = isBundled,
                            isEssential = isEssential,
                            pluginPath = file,
                            useCoreClassLoader = useCoreClassLoader)?.let {
        it.jarFiles = pluginJarFiles
        return it
      }
    }
  }

  // not found, ok, let's check classes (but only for unbundled plugins)
  if (!isBundled
      || isUnitTestMode) {
    val classesDir = file.resolve("classes")
    sequenceOf(
      classesDir,
      file,
    ).firstNotNullOfOrNull {
      loadDescriptorFromDir(
        file = it,
        descriptorRelativePath = PluginManagerCore.PLUGIN_XML_PATH,
        pluginPath = file,
        context = parentContext,
        isBundled = isBundled,
        isEssential = isEssential,
        pathResolver = pathResolver,
        useCoreClassLoader = useCoreClassLoader,
      )
    }?.let {
      val classPath = ArrayList<Path>(pluginJarFiles.size + 1)
      classPath.add(classesDir)
      classPath.addAll(pluginJarFiles)
      it.jarFiles = classPath
      return it
    }
  }
  return null
}

private fun resolveArchives(path: Path): List<Path> {
  return try {
    Files.newDirectoryStream(path.resolve("lib")).use { stream ->
      stream.filter {
        val childPath = it.toString()
        childPath.endsWith(".jar", ignoreCase = true)
        || childPath.endsWith(".zip", ignoreCase = true)
      }
    }
  }
  catch (e: NoSuchFileException) {
    emptyList()
  }
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
  val t = StringTokenizer(pathProperty, File.pathSeparatorChar + ",")
  while (t.hasMoreTokens()) {
    val s = t.nextToken()
    val file = Paths.get(s)
    loadDescriptorFromFileOrDir(file = file,
                                context = context,
                                pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                isBundled = false,
                                isEssential = false,
                                isDirectory = Files.isDirectory(file),
                                useCoreClassLoader = useCoreClassLoaderForPluginsFromProperty)?.let {
      // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
      result.add(it, overrideUseIfCompatible = true)
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
  val isInDevServerMode = java.lang.Boolean.getBoolean("idea.use.dev.build.server")
  val pathResolver = ClassPathXmlPathResolver(
    classLoader = classLoader,
    isRunningFromSources = isRunningFromSources && !isInDevServerMode,
  )
  val useCoreClassLoader = pathResolver.isRunningFromSources ||
                           platformPrefix.startsWith("CodeServer") ||
                           java.lang.Boolean.getBoolean("idea.force.use.core.classloader")
  // should be the only plugin in lib (only for Ultimate and WebStorm for now)
  if ((platformPrefix == PlatformUtils.IDEA_PREFIX || platformPrefix == PlatformUtils.WEB_PREFIX) &&
      (isInDevServerMode || (!isUnitTestMode && !isRunningFromSources))) {
    loadCoreProductPlugin(getResourceReader(PluginManagerCore.PLUGIN_XML_PATH, classLoader)!!,
                          context = context,
                          pathResolver = pathResolver,
                          useCoreClassLoader = useCoreClassLoader)
  }
  else {
    val fileName = "${platformPrefix}Plugin.xml"
    getResourceReader("${PluginManagerCore.META_INF}$fileName", classLoader)?.let {
      loadCoreProductPlugin(it, context, pathResolver, useCoreClassLoader)
    }

    val urlToFilename = collectPluginFilesInClassPath(classLoader)
    if (!urlToFilename.isEmpty()) {
      activity = activity.endAndStart("plugin from classpath loading")
      pool.invoke(LoadDescriptorsFromClassPathAction(urlToFilename = urlToFilename,
                                                     context = context,
                                                     pathResolver = pathResolver,
                                                     useCoreClassLoader = useCoreClassLoader))
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

private fun getResourceReader(path: String, classLoader: ClassLoader): XMLStreamReader2? {
  if (classLoader is UrlClassLoader) {
    return createNonCoalescingXmlStreamReader(classLoader.getResourceAsBytes(path, false) ?: return null, path)
  }
  else {
    return createNonCoalescingXmlStreamReader(classLoader.getResourceAsStream(path) ?: return null, path)
  }
}

private fun loadCoreProductPlugin(reader: XMLStreamReader2,
                                  context: DescriptorListLoadingContext,
                                  pathResolver: ClassPathXmlPathResolver,
                                  useCoreClassLoader: Boolean) {
  val dataLoader = object : DataLoader {
    override val pool: ZipFilePool
      get() = throw IllegalStateException("must be not called")

    override val emptyDescriptorIfCannotResolve: Boolean
      get() = true

    override fun load(path: String) = throw IllegalStateException("must be not called")

    override fun toString() = "product classpath"
  }

  val raw = readModuleDescriptor(reader,
                                 readContext = context,
                                 pathResolver = pathResolver,
                                 dataLoader = dataLoader,
                                 includeBase = null,
                                 readInto = null)
  val descriptor = IdeaPluginDescriptorImpl(raw = raw,
                                            path = Paths.get(PathManager.getLibPath()),
                                            isBundled = true,
                                            id = null,
                                            moduleName = null,
                                            useCoreClassLoader = useCoreClassLoader)
  descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = context, isSub = false, dataLoader = dataLoader)
  context.result.add(descriptor, overrideUseIfCompatible = false)
}

private fun collectPluginFilesInClassPath(loader: ClassLoader): Map<URL, String> {
  val urlToFilename = LinkedHashMap<URL, String>()
  try {
    val enumeration = loader.getResources(PluginManagerCore.PLUGIN_XML_PATH)
    while (enumeration.hasMoreElements()) {
      urlToFilename.put(enumeration.nextElement(), PluginManagerCore.PLUGIN_XML)
    }
  }
  catch (e: IOException) {
    LOG.warn(e)
  }
  return urlToFilename
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
                                               context = context,
                                               pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                               isBundled = false,
                                               isEssential = false,
                                               isDirectory = false,
                                               useCoreClassLoader = false)
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
                                           context = context,
                                           pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                           isBundled = false,
                                           isEssential = false,
                                           isDirectory = true,
                                           useCoreClassLoader = false)
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
                                       context = context,
                                       pathResolver = pathResolver,
                                       isBundled = isBundled,
                                       isEssential = false,
                                       isDirectory = Files.isDirectory(file),
                                       useCoreClassLoader = false)
  }
}

@Throws(ExecutionException::class, InterruptedException::class)
fun getDescriptorsToMigrate(dir: Path,
                            compatibleBuildNumber: BuildNumber?,
                            bundledPluginsPath: Path?,
                            brokenPluginVersions: Map<PluginId, Set<String>>?,
                            pluginsToMigrate: MutableList<IdeaPluginDescriptor?>,
                            incompatiblePlugins: MutableList<IdeaPluginDescriptor?>) {
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
  val urlToFilename = collectPluginFilesInClassPath(loader)
  val buildNumber = BuildNumber.fromString("2042.42")!!
  val context = DescriptorListLoadingContext(disabledPlugins = Collections.emptySet(),
                                             result = PluginLoadingResult(brokenPluginVersions = emptyMap(),
                                                                          productBuildNumber = Supplier { buildNumber },
                                                                          checkModuleDependencies = false))
  LoadDescriptorsFromClassPathAction(
    urlToFilename = urlToFilename,
    context = context,
    pathResolver = ClassPathXmlPathResolver(loader, isRunningFromSources = false),
    useCoreClassLoader = true
  ).compute()
  context.result.finishLoading()
  return context.result.getEnabledPlugins()
}

private class LoadDescriptorsFromDirAction(private val dir: Path,
                                           private val context: DescriptorListLoadingContext,
                                           private val isBundled: Boolean) : RecursiveAction() {

  override fun compute() {
    try {
      val tasks: List<RecursiveTask<IdeaPluginDescriptorImpl?>> = Files.newDirectoryStream(dir).use { dirStream ->
        dirStream.map { file ->
          object : RecursiveTask<IdeaPluginDescriptorImpl?>() {
            override fun compute(): IdeaPluginDescriptorImpl? {
              return loadDescriptorFromFileOrDir(
                file = file,
                context = context,
                pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                isBundled = isBundled,
                isDirectory = Files.isDirectory(file),
                isEssential = false,
                useCoreClassLoader = false,
              )
            }
          }
        }
      }

      ForkJoinTask.invokeAll(tasks)
      for (task in tasks) {
        task.rawResult?.let {
          context.result.add(it,  /* overrideUseIfCompatible = */false)
        }
      }
    }
    catch (ignore: IOException) {
    }
  }
}

// urls here expected to be a file urls to plugin.xml
private class LoadDescriptorsFromClassPathAction(private val urlToFilename: Map<URL, String>,
                                                 private val context: DescriptorListLoadingContext,
                                                 private val pathResolver: ClassPathXmlPathResolver,
                                                 private val useCoreClassLoader: Boolean) : RecursiveAction() {
  public override fun compute() {
    val tasks = ArrayList<ForkJoinTask<IdeaPluginDescriptorImpl?>>(urlToFilename.size)
    urlToFilename.forEach(BiConsumer { url, filename ->
      tasks.add(object : RecursiveTask<IdeaPluginDescriptorImpl?>() {
        override fun compute(): IdeaPluginDescriptorImpl? {
          return try {
            loadDescriptorFromResource(resource = url, filename = filename)
          }
          catch (e: Throwable) {
            LOG.info("Cannot load $url", e)
            null
          }
        }
      })
    })

    val result = context.result
    ForkJoinTask.invokeAll(tasks)
    for (task in tasks) {
      task.rawResult?.let {
        result.add(it, overrideUseIfCompatible = false)
      }
    }
  }

  // filename - plugin.xml or ${platformPrefix}Plugin.xml
  private fun loadDescriptorFromResource(resource: URL, filename: String): IdeaPluginDescriptorImpl? {
    val file = Paths.get(UrlClassLoader.urlToFilePath(resource.path))
    var closeable: Closeable? = null
    val dataLoader: DataLoader
    val basePath: Path
    try {
      val input: InputStream
      when {
        URLUtil.FILE_PROTOCOL == resource.protocol -> {
          basePath = file.parent.parent
          dataLoader = LocalFsDataLoader(basePath)
          input = Files.newInputStream(file)
        }
        URLUtil.JAR_PROTOCOL == resource.protocol -> {
          // support for unpacked plugins in classpath, e.g. .../community/build/dependencies/build/kotlin/Kotlin/lib/kotlin-plugin.jar
          basePath = file.parent?.takeIf { !it.endsWith("lib") }?.parent ?: file

          val pool = if (context.transient) null else ZipFilePool.POOL
          if (pool == null) {
            val zipFile = ZipFile(file.toFile(), StandardCharsets.UTF_8)
            closeable = zipFile
            dataLoader = JavaZipFileDataLoader(zipFile)
          }
          else {
            dataLoader = ImmutableZipFileDataLoader(pool.load(file), file, pool)
          }

          input = dataLoader.load("META-INF/$filename") ?: return null
        }
        else -> return null
      }

      val raw = readModuleDescriptor(input = input,
                                     readContext = context,
                                     pathResolver = pathResolver,
                                     dataLoader = dataLoader,
                                     includeBase = null,
                                     readInto = null,
                                     locationSource = file.toString())
      // it is very important to not set useCoreClassLoader = true blindly
      // - product modules must uses own class loader if not running from sources
      val descriptor = IdeaPluginDescriptorImpl(raw = raw, path = basePath, isBundled = true, id = null, moduleName = null,
                                                useCoreClassLoader = useCoreClassLoader)
      descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = context, isSub = false, dataLoader = dataLoader)
      // do not set jarFiles by intention - doesn't make sense
      return descriptor
    }
    finally {
      closeable?.close()
    }
  }
}