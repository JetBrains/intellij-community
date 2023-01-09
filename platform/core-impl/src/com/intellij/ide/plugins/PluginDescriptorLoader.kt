// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty", "ReplacePutWithAssignment")
@file:JvmName("PluginDescriptorLoader")
@file:Internal

package com.intellij.ide.plugins

import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.Decompressor
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import kotlinx.coroutines.*
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
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
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.zip.ZipFile
import javax.xml.stream.XMLStreamException
import kotlin.io.path.name

private val LOG: Logger
  get() = PluginManagerCore.getLogger()

@TestOnly
fun loadDescriptor(file: Path, parentContext: DescriptorListLoadingContext): IdeaPluginDescriptorImpl? {
  return loadDescriptorFromFileOrDir(file = file,
                                     context = parentContext,
                                     pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                     isBundled = false,
                                     isEssential = false,
                                     isDirectory = Files.isDirectory(file),
                                     useCoreClassLoader = false,
                                     pool = null)
}

internal fun loadForCoreEnv(pluginRoot: Path, fileName: String): IdeaPluginDescriptorImpl? {
  val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val parentContext = DescriptorListLoadingContext()
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
    return runBlocking {
      loadDescriptorFromJar(file = pluginRoot,
                            fileName = fileName,
                            pathResolver = pathResolver,
                            parentContext = parentContext,
                            isBundled = true,
                            isEssential = true,
                            pluginPath = null,
                            useCoreClassLoader = false,
                            pool = null)
    }
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
                                  pluginPath: Path?,
                                  pool: ZipFilePool?): IdeaPluginDescriptorImpl? {
  var closeable: Closeable? = null
  try {
    val dataLoader = if (pool == null) {
      val zipFile = ZipFile(file.toFile(), StandardCharsets.UTF_8)
      closeable = zipFile
      JavaZipFileDataLoader(zipFile)
    }
    else {
      ImmutableZipFileDataLoader(pool.load(file), file, pool)
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
    parentContext.reportCannotLoad(file, e)
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

@VisibleForTesting
fun loadDescriptorFromFileOrDir(
  file: Path,
  context: DescriptorListLoadingContext,
  pathResolver: PathResolver,
  isBundled: Boolean,
  isEssential: Boolean,
  isDirectory: Boolean,
  useCoreClassLoader: Boolean,
  isUnitTestMode: Boolean = false,
  pool: ZipFilePool?,
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
        pool = pool,
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
                            useCoreClassLoader = useCoreClassLoader,
                            pool = pool)
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
  pool: ZipFilePool?,
): IdeaPluginDescriptorImpl? {
  val pluginJarFiles = resolveArchives(file)
  if (!pluginJarFiles.isNullOrEmpty()) {
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
                            useCoreClassLoader = useCoreClassLoader,
                            pool = pool)?.let {
        it.jarFiles = pluginJarFiles
        return it
      }
    }
  }

  // not found, ok, let's check classes (but only for unbundled plugins)
  if (!isBundled || isUnitTestMode) {
    val classesDir = file.resolve("classes")
    sequenceOf(classesDir, file)
      .firstNotNullOfOrNull {
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
        if (pluginJarFiles.isNullOrEmpty()) {
          it.jarFiles = Collections.singletonList(classesDir)
        }
        else {
          val classPath = ArrayList<Path>(pluginJarFiles.size + 1)
          classPath.add(classesDir)
          classPath.addAll(pluginJarFiles)
          it.jarFiles = classPath
        }
        return it
      }
  }
  return null
}

private fun resolveArchives(path: Path): MutableList<Path>? {
  try {
    return Files.newDirectoryStream(path.resolve("lib")).use { stream ->
      stream.filterTo(ArrayList()) {
        val childPath = it.toString()
        childPath.endsWith(".jar", ignoreCase = true) || childPath.endsWith(".zip", ignoreCase = true)
      }
    }
  }
  catch (e: NoSuchFileException) {
    return null
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

private fun CoroutineScope.loadDescriptorsFromProperty(context: DescriptorListLoadingContext,
                                                       pool: ZipFilePool?): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val pathProperty = System.getProperty("plugin.path") ?: return emptyList()

  // gradle-intellij-plugin heavily depends on this property in order to have core class loader plugins during tests
  val useCoreClassLoaderForPluginsFromProperty = java.lang.Boolean.getBoolean("idea.use.core.classloader.for.plugin.path")
  val t = StringTokenizer(pathProperty, File.pathSeparatorChar + ",")
  val list = mutableListOf<Deferred<IdeaPluginDescriptorImpl?>>()
  while (t.hasMoreTokens()) {
    val file = Paths.get(t.nextToken())
    list.add(async {
      loadDescriptorFromFileOrDir(
        file = file,
        context = context,
        pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
        isBundled = false,
        isEssential = false,
        isDirectory = Files.isDirectory(file),
        useCoreClassLoader = useCoreClassLoaderForPluginsFromProperty,
        pool = pool,
      )
    })
  }
  return list
}

@Suppress("DeferredIsResult")
internal fun CoroutineScope.scheduleLoading(zipFilePoolDeferred: Deferred<ZipFilePool>?): Deferred<PluginSet> {
  val resultDeferred = async(CoroutineName("plugin descriptor loading") + Dispatchers.Default) {
    val isUnitTestMode = PluginManagerCore.isUnitTestMode
    val isRunningFromSources = PluginManagerCore.isRunningFromSources()
    val result = DescriptorListLoadingContext(
      isMissingSubDescriptorIgnored = true,
      isMissingIncludeIgnored = isUnitTestMode,
      checkOptionalConfigFileUniqueness = isUnitTestMode || isRunningFromSources,
    ).use { context ->
      context to loadDescriptors(
        context = context,
        isUnitTestMode = isUnitTestMode,
        isRunningFromSources = isRunningFromSources,
        zipFilePoolDeferred = zipFilePoolDeferred,
      )
    }
    result
  }
  val pluginSetDeferred = async(Dispatchers.Default) {
    val pair = resultDeferred.await()
    PluginManagerCore.initializeAndSetPlugins(pair.first, pair.second, PluginManagerCore::class.java.classLoader)
  }

  // logging is no not as a part of plugin set job for performance reasons
  launch(Dispatchers.Default) {
    val pair = resultDeferred.await()
    logPlugins(plugins = pluginSetDeferred.await().allPlugins, context = pair.first, loadingResult = pair.second)
  }
  return pluginSetDeferred
}

private fun logPlugins(plugins: Collection<IdeaPluginDescriptorImpl>,
                       context: DescriptorListLoadingContext,
                       loadingResult: PluginLoadingResult) {
  if (AppMode.isDisableNonBundledPlugins()) {
    LOG.info("Running with disableThirdPartyPlugins argument, third-party plugins will be disabled")
  }

  val bundled = StringBuilder()
  val disabled = StringBuilder()
  val custom = StringBuilder()
  val disabledPlugins = HashSet<PluginId>()
  for (descriptor in plugins) {
    var target: StringBuilder
    val pluginId = descriptor.pluginId
    target = if (!descriptor.isEnabled) {
      if (!context.isPluginDisabled(pluginId)) {
        // plugin will be logged as part of "Problems found loading plugins"
        continue
      }
      disabledPlugins.add(pluginId)
      disabled
    }
    else if (descriptor.isBundled || PluginManagerCore.SPECIAL_IDEA_PLUGIN_ID == pluginId) {
      bundled
    }
    else {
      custom
    }
    appendPlugin(descriptor, target)
  }

  for ((pluginId, descriptor) in loadingResult.getIncompleteIdMap()) {
    // log only explicitly disabled plugins
    if (context.isPluginDisabled(pluginId) && !disabledPlugins.contains(pluginId)) {
      appendPlugin(descriptor, disabled)
    }
  }

  val log = LOG
  log.info("Loaded bundled plugins: $bundled")
  if (custom.isNotEmpty()) {
    log.info("Loaded custom plugins: $custom")
  }
  if (disabled.isNotEmpty()) {
    log.info("Disabled plugins: $disabled")
  }
}

private fun appendPlugin(descriptor: IdeaPluginDescriptor, target: StringBuilder) {
  if (target.isNotEmpty()) {
    target.append(", ")
  }
  target.append(descriptor.name)
  val version = descriptor.version
  if (version != null) {
    target.append(" (").append(version).append(')')
  }
}

// used and must be used only by Rider
@Suppress("unused")
@Internal
suspend fun getLoadedPluginsForRider(): List<IdeaPluginDescriptorImpl?> {
  PluginManagerCore.getNullablePluginSet()?.enabledPlugins?.let {
    return it
  }

  val isUnitTestMode = PluginManagerCore.isUnitTestMode
  val isRunningFromSources = PluginManagerCore.isRunningFromSources()
  return DescriptorListLoadingContext(
    isMissingSubDescriptorIgnored = true,
    isMissingIncludeIgnored = isUnitTestMode,
    checkOptionalConfigFileUniqueness = isUnitTestMode || isRunningFromSources,
  ).use { context ->
    val result = loadDescriptors(context = context, isUnitTestMode = isUnitTestMode, isRunningFromSources = isRunningFromSources)
    PluginManagerCore.initializeAndSetPlugins(context, result, PluginManagerCore::class.java.classLoader).enabledPlugins
  }
}

@Internal
@Deprecated("do not use")
fun loadDescriptorsForDeprecatedWizard(): PluginLoadingResult {
  return runBlocking {
    val isUnitTestMode = PluginManagerCore.isUnitTestMode
    val isRunningFromSources = PluginManagerCore.isRunningFromSources()
    DescriptorListLoadingContext(
      isMissingSubDescriptorIgnored = true,
      isMissingIncludeIgnored = isUnitTestMode,
      checkOptionalConfigFileUniqueness = isUnitTestMode || isRunningFromSources,
    ).use { context ->
      loadDescriptors(context = context, isUnitTestMode = isUnitTestMode, isRunningFromSources = isRunningFromSources)
    }
  }
}

/**
 * Think twice before use and get approve from core team.
 *
 * Returns enabled plugins only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Internal
suspend fun loadDescriptors(
  context: DescriptorListLoadingContext,
  isUnitTestMode: Boolean = PluginManagerCore.isUnitTestMode,
  isRunningFromSources: Boolean,
  zipFilePoolDeferred: Deferred<ZipFilePool>? = null,
): PluginLoadingResult {
  val listDeferred: List<Deferred<IdeaPluginDescriptorImpl?>>
  val extraListDeferred: List<Deferred<IdeaPluginDescriptorImpl?>>
  coroutineScope {
    val zipFilePool = if (context.transient) null else zipFilePoolDeferred?.await()
    withContext(Dispatchers.IO) {
      listDeferred = loadDescriptorsFromDirs(
        context = context,
        customPluginDir = Paths.get(PathManager.getPluginsPath()),
        isUnitTestMode = isUnitTestMode,
        isRunningFromSources = isRunningFromSources,
        zipFilePool = zipFilePool,
      )
      extraListDeferred = loadDescriptorsFromProperty(context, zipFilePool)
    }
  }

  val buildNumber = context.productBuildNumber()
  val loadingResult = PluginLoadingResult()
  loadingResult.addAll(descriptors = listDeferred.map { it.getCompleted() }, overrideUseIfCompatible = false, productBuildNumber = buildNumber)
  // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
  loadingResult.addAll(descriptors = extraListDeferred.map { it.getCompleted() }, overrideUseIfCompatible = true, productBuildNumber = buildNumber)

  if (isUnitTestMode && loadingResult.enabledPluginsById.size <= 1) {
    // we're running in unit test mode, but the classpath doesn't contain any plugins; try to load bundled plugins anyway
    loadingResult.addAll(
      descriptors = coroutineScope {
        loadDescriptorsFromDir(
          dir = Paths.get(PathManager.getPreInstalledPluginsPath()),
          context = context,
          isBundled = true,
          pool = if (context.transient) null else zipFilePoolDeferred?.await()
        )
      }.awaitAll(),
      overrideUseIfCompatible = false,
      productBuildNumber = buildNumber
    )
  }
  return loadingResult
}

private fun CoroutineScope.loadDescriptorsFromDirs(
  context: DescriptorListLoadingContext,
  customPluginDir: Path,
  bundledPluginDir: Path? = null,
  isUnitTestMode: Boolean = PluginManagerCore.isUnitTestMode,
  isRunningFromSources: Boolean = PluginManagerCore.isRunningFromSources(),
  zipFilePool: ZipFilePool?,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val isInDevServerMode = AppMode.isDevServer()

  val platformPrefixProperty = PlatformUtils.getPlatformPrefix()
  val platformPrefix = if (platformPrefixProperty == PlatformUtils.QODANA_PREFIX) {
    System.getProperty("idea.parent.prefix", PlatformUtils.IDEA_PREFIX)
  }
  else {
    platformPrefixProperty
  }

  val root = loadCoreModules(context = context,
                             platformPrefix = platformPrefix,
                             isUnitTestMode = isUnitTestMode,
                             isInDevServerMode = isInDevServerMode,
                             isRunningFromSources = isRunningFromSources,
                             pool = zipFilePool)

  val custom = loadDescriptorsFromDir(dir = customPluginDir, context = context, isBundled = false, pool = zipFilePool)

  val effectiveBundledPluginDir = bundledPluginDir ?: if (isUnitTestMode) {
    null
  }
  else if (isInDevServerMode) {
    Paths.get(PathManager.getHomePath(), "out/dev-run", AppMode.getDevBuildRunDirName(platformPrefix), "plugins")
  }
  else {
    Paths.get(PathManager.getPreInstalledPluginsPath())
  }

  val bundled = if (effectiveBundledPluginDir == null) {
    emptyList()
  }
  else {
    loadDescriptorsFromDir(dir = effectiveBundledPluginDir, context = context, isBundled = true, pool = zipFilePool)
  }

  return (root + custom + bundled)
}

private fun CoroutineScope.loadCoreModules(context: DescriptorListLoadingContext,
                                           platformPrefix: String,
                                           isUnitTestMode: Boolean,
                                           isInDevServerMode: Boolean,
                                           isRunningFromSources: Boolean,
                                           pool: ZipFilePool?): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val classLoader = DescriptorListLoadingContext::class.java.classLoader
  val pathResolver = ClassPathXmlPathResolver(classLoader = classLoader, isRunningFromSources = isRunningFromSources && !isInDevServerMode)
  val useCoreClassLoader = pathResolver.isRunningFromSources ||
                           platformPrefix.startsWith("CodeServer") ||
                           java.lang.Boolean.getBoolean("idea.force.use.core.classloader")
  // should be the only plugin in lib (only for Ultimate and WebStorm for now)
  val rootModuleDescriptors = if ((platformPrefix == PlatformUtils.IDEA_PREFIX || platformPrefix == PlatformUtils.WEB_PREFIX) &&
                                  (isInDevServerMode || (!isUnitTestMode && !isRunningFromSources))) {
    Collections.singletonList(async {
      loadCoreProductPlugin(getResourceReader(PluginManagerCore.PLUGIN_XML_PATH, classLoader)!!,
                            context = context,
                            pathResolver = pathResolver,
                            useCoreClassLoader = useCoreClassLoader)
    })
  }
  else {
    val fileName = "${platformPrefix}Plugin.xml"
    var result = listOf(async {
      getResourceReader("${PluginManagerCore.META_INF}$fileName", classLoader)?.let {
        loadCoreProductPlugin(it, context, pathResolver, useCoreClassLoader)
      }
    })

    val urlToFilename = collectPluginFilesInClassPath(classLoader)
    if (!urlToFilename.isEmpty()) {
      @Suppress("SuspiciousCollectionReassignment")
      result += loadDescriptorsFromClassPath(urlToFilename = urlToFilename,
                                             context = context,
                                             pathResolver = pathResolver,
                                             useCoreClassLoader = useCoreClassLoader,
                                             pool = pool)
    }

    result
  }
  return rootModuleDescriptors
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
                                  useCoreClassLoader: Boolean): IdeaPluginDescriptorImpl {
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
  return descriptor
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

@Throws(IOException::class)
@RequiresBackgroundThread
fun loadDescriptorFromArtifact(file: Path, buildNumber: BuildNumber?): IdeaPluginDescriptorImpl? {
  val context = DescriptorListLoadingContext(isMissingSubDescriptorIgnored = true,
                                             productBuildNumber = { buildNumber ?: PluginManagerCore.getBuildNumber() },
                                             transient = true)

  val descriptor = runBlocking {
    loadDescriptorFromFileOrDir(file = file,
                                context = context,
                                pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                isBundled = false,
                                isEssential = false,
                                isDirectory = false,
                                useCoreClassLoader = false,
                                pool = null)
  }
  if (descriptor != null || !file.toString().endsWith(".zip")) {
    return descriptor
  }

  val outputDir = Files.createTempDirectory("plugin")!!
  try {
    Decompressor.Zip(file)
      .withZipExtensions()
      .extract(outputDir)
    try {
      //org.jetbrains.intellij.build.io.ZipArchiveOutputStream may add __index__ entry to the plugin zip, we need to ignore it here
      val rootDir = NioFiles.list(outputDir).firstOrNull { it.name != "__index__" }
      if (rootDir != null) {
        return runBlocking {
          loadDescriptorFromFileOrDir(file = rootDir,
                                      context = context,
                                      pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                      isBundled = false,
                                      isEssential = false,
                                      isDirectory = true,
                                      useCoreClassLoader = false,
                                      pool = null)
        }
      }
    }
    catch (ignore: NoSuchFileException) {
    }
  }
  finally {
    NioFiles.deleteRecursively(outputDir)
  }

  return null
}

fun loadDescriptor(
  file: Path,
  isBundled: Boolean,
  pathResolver: PathResolver,
): IdeaPluginDescriptorImpl? {
  DescriptorListLoadingContext().use { context ->
    return runBlocking {
      loadDescriptorFromFileOrDir(
        file = file,
        context = context,
        pathResolver = pathResolver,
        isBundled = isBundled,
        isEssential = false,
        isDirectory = Files.isDirectory(file),
        useCoreClassLoader = false,
        pool = null,
      )
    }
  }
}

@Throws(ExecutionException::class, InterruptedException::class, IOException::class)
fun loadDescriptors(
  customPluginDir: Path,
  bundledPluginDir: Path?,
  brokenPluginVersions: Map<PluginId, Set<String?>>?,
  productBuildNumber: BuildNumber?,
): PluginLoadingResult {
  return DescriptorListLoadingContext(
    disabledPlugins = emptySet(),
    brokenPluginVersions = brokenPluginVersions ?: PluginManagerCore.getBrokenPluginVersions(),
    productBuildNumber = { productBuildNumber ?: PluginManagerCore.getBuildNumber() },
    isMissingIncludeIgnored = true,
    isMissingSubDescriptorIgnored = true,
  ).use { context ->
    runBlocking {
      val result = PluginLoadingResult()
      val descriptors = loadDescriptorsFromDirs(
        context = context,
        customPluginDir = customPluginDir,
        bundledPluginDir = bundledPluginDir,
        zipFilePool = null,
      ).awaitAll()

      result.addAll(
        descriptors = descriptors,
        overrideUseIfCompatible = false,
        productBuildNumber = context.productBuildNumber(),
      )
      result
    }
  }
}

@TestOnly
fun testLoadDescriptorsFromClassPath(loader: ClassLoader): List<IdeaPluginDescriptor> {
  val urlToFilename = collectPluginFilesInClassPath(loader)
  val buildNumber = BuildNumber.fromString("2042.42")!!
  val context = DescriptorListLoadingContext(disabledPlugins = Collections.emptySet(),
                                             brokenPluginVersions = emptyMap(),
                                             productBuildNumber = { buildNumber })
  return runBlocking {
    val result = PluginLoadingResult(checkModuleDependencies = false)
    result.addAll(loadDescriptorsFromClassPath(
      urlToFilename = urlToFilename,
      context = context,
      pathResolver = ClassPathXmlPathResolver(loader, isRunningFromSources = false),
      useCoreClassLoader = true,
      pool = if (context.transient) null else ZipFilePool.POOL,
    ).awaitAll(), overrideUseIfCompatible = false, productBuildNumber = buildNumber)
    result.enabledPlugins
  }
}

private fun CoroutineScope.loadDescriptorsFromDir(dir: Path,
                                                  context: DescriptorListLoadingContext,
                                                  isBundled: Boolean,
                                                  pool: ZipFilePool?): List<Deferred<IdeaPluginDescriptorImpl?>> {
  if (!Files.isDirectory(dir)) {
    return emptyList()
  }

  return Files.newDirectoryStream(dir).use { dirStream ->
    dirStream.map { file ->
      async {
        loadDescriptorFromFileOrDir(
          file = file,
          context = context,
          pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
          isBundled = isBundled,
          isDirectory = Files.isDirectory(file),
          isEssential = false,
          useCoreClassLoader = false,
          pool = pool,
        )
      }
    }
  }
}

// urls here expected to be a file urls to plugin.xml
private fun CoroutineScope.loadDescriptorsFromClassPath(
  urlToFilename: Map<URL, String>,
  context: DescriptorListLoadingContext,
  pathResolver: ClassPathXmlPathResolver,
  useCoreClassLoader: Boolean,
  pool: ZipFilePool?,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  return urlToFilename.map { (url, filename) ->
    async {
      loadDescriptorFromResource(resource = url,
                                 filename = filename,
                                 context = context,
                                 pathResolver = pathResolver,
                                 useCoreClassLoader = useCoreClassLoader,
                                 pool = pool)
    }
  }
}

// filename - plugin.xml or ${platformPrefix}Plugin.xml
private fun loadDescriptorFromResource(
  resource: URL,
  filename: String,
  context: DescriptorListLoadingContext,
  pathResolver: ClassPathXmlPathResolver,
  useCoreClassLoader: Boolean,
  pool: ZipFilePool?,
): IdeaPluginDescriptorImpl? {
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
    val descriptor = IdeaPluginDescriptorImpl(raw = raw,
                                              path = basePath,
                                              isBundled = true,
                                              id = null,
                                              moduleName = null,
                                              useCoreClassLoader = useCoreClassLoader)
    descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = context, isSub = false, dataLoader = dataLoader)
    // do not set jarFiles by intention - doesn't make sense
    return descriptor
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.info("Cannot load $resource", e)
    return null
  }
  finally {
    closeable?.close()
  }
}