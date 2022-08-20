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
import com.intellij.util.io.Decompressor
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
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
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.zip.ZipFile
import javax.xml.stream.XMLStreamException
import kotlin.io.path.name

private val LOG: Logger
  get() = PluginManagerCore.getLogger()

@TestOnly
suspend fun loadDescriptor(file: Path, parentContext: DescriptorListLoadingContext): IdeaPluginDescriptorImpl? {
  return loadDescriptorFromFileOrDir(file = file,
                                                              context = parentContext,
                                                              pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                                                              isBundled = false,
                                                              isEssential = false,
                                                              useCoreClassLoader = false,
                                                              isUnitTestMode = true,
                                                              pool = null)
}

internal fun loadForCoreEnv(pluginRoot: Path, fileName: String): IdeaPluginDescriptorImpl? {
  val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val parentContext = DescriptorListLoadingContext()
  if (Files.isDirectory(pluginRoot)) {
    val descriptorRelativePath = "${PluginManagerCore.META_INF}$fileName"
    val input = try {
      Files.readAllBytes(pluginRoot.resolve(descriptorRelativePath))
    }
    catch (e: NoSuchFileException) {
      return null
    }

    val dataLoader = LocalFsDataLoader(pluginRoot)
    val raw = readModuleDescriptor(input = input,
                                   readContext = parentContext,
                                   pathResolver = pathResolver,
                                   dataLoader = dataLoader,
                                   includeBase = null,
                                   readInto = null,
                                   locationSource = pluginRoot.toString())
    val descriptor = IdeaPluginDescriptorImpl(raw = raw,
                                              path = pluginRoot,
                                              isBundled = true,
                                              id = null,
                                              moduleName = null,
                                              useCoreClassLoader = false)
    descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = parentContext, isSub = false, dataLoader = dataLoader)
    descriptor.jarFiles = Collections.singletonList(pluginRoot)
    return descriptor
  }
  else {
    var closeable: Closeable? = null
    try {
      val zipFile = ZipFile(pluginRoot.toFile(), StandardCharsets.UTF_8)
      closeable = zipFile
      val dataLoader = JavaZipFileDataLoader(zipFile)
      val raw = readModuleDescriptor(input = dataLoader.load("META-INF/$fileName") ?: return null,
                                     readContext = parentContext,
                                     pathResolver = pathResolver,
                                     dataLoader = dataLoader,
                                     includeBase = null,
                                     readInto = null,
                                     locationSource = pluginRoot.toString())

      val descriptor = IdeaPluginDescriptorImpl(raw = raw,
                                                path = pluginRoot,
                                                isBundled = true,
                                                id = null,
                                                moduleName = null,
                                                useCoreClassLoader = false)
      descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = parentContext, isSub = false, dataLoader = dataLoader)
      descriptor.jarFiles = listOf(descriptor.pluginPath)
      return descriptor
    }
    catch (e: Throwable) {
      throw if (e is XMLStreamException) RuntimeException("Cannot read $pluginRoot", e) else e
    }
    finally {
      closeable?.close()
    }
  }
}

private fun readDescriptor(item: Item,
                           pathResolver: PathResolver,
                           parentContext: DescriptorListLoadingContext,
                           isBundled: Boolean,
                           isEssential: Boolean,
                           useCoreClassLoader: Boolean): IdeaPluginDescriptorImpl? {
  try {
    val dataLoader = item.dataLoader
    val raw = readModuleDescriptor(
      input = item.data,
      readContext = parentContext,
      pathResolver = pathResolver,
      dataLoader = dataLoader,
      includeBase = null,
      readInto = null,
      locationSource = item.file.toString()
    )

    val descriptor = IdeaPluginDescriptorImpl(
      raw = raw,
      path = item.file,
      isBundled = isBundled,
      id = null,
      moduleName = null,
      useCoreClassLoader = useCoreClassLoader
    )
    descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = parentContext, isSub = false, dataLoader = dataLoader)
    descriptor.jarFiles = item.files
    return descriptor
  }
  catch (e: Throwable) {
    if (isEssential) {
      throw if (e is XMLStreamException) RuntimeException("Cannot read ${item.file}", e) else e
    }
    parentContext.reportCannotLoad(item.file, e)
    return null
  }
  finally {
    (item.dataLoader as? Closeable)?.close()
  }
}

private class JavaZipFileDataLoader(private val file: ZipFile) : DataLoader, Closeable {
  override val pool: ZipFilePool?
    get() = null

  override fun load(path: String): ByteArray? {
    val entry = file.getEntry(if (path[0] == '/') path.substring(1) else path) ?: return null
    return file.getInputStream(entry)?.readBytes()
  }

  override fun toString() = file.toString()

  override fun close() {
    file.close()
  }
}

@VisibleForTesting
suspend fun loadDescriptorFromFileOrDir(
  file: Path,
  context: DescriptorListLoadingContext,
  pathResolver: PathResolver,
  isBundled: Boolean,
  isEssential: Boolean,
  useCoreClassLoader: Boolean,
  isUnitTestMode: Boolean,
  pool: ZipFilePool?,
): IdeaPluginDescriptorImpl? {
  val channel = Channel<Item>(capacity = 1)
  withContext(Dispatchers.IO) {
    loadDescriptorFromFileOrDir(file, pool, isBundled, isUnitTestMode = isUnitTestMode, channel)
  }
  val item = channel.tryReceive().getOrNull()
  channel.close()
  return readDescriptor(item ?: return null, pathResolver, context, isBundled, isEssential, useCoreClassLoader)
}

private class Item(
  @JvmField val files: List<Path>,
  @JvmField val file: Path,
  @JvmField val data: ByteArray,
  @JvmField val dataLoader: DataLoader,
)

// [META-INF] [classes] lib/*.jar
private fun loadFromPluginDir(
  dir: Path,
  isBundled: Boolean,
  isUnitTestMode: Boolean,
  pool: ZipFilePool?,
): Item? {
  val pluginJarFiles = resolveArchives(dir)
  if (!pluginJarFiles.isNullOrEmpty()) {
    if (pluginJarFiles.size > 1) {
      putMoreLikelyPluginJarsFirst(dir, pluginJarFiles)
    }
    for (jarFile in pluginJarFiles) {
      var closeable: Closeable?
      val dataLoader = if (pool == null) {
        val zipFile = ZipFile(jarFile.toFile(), StandardCharsets.UTF_8)
        closeable = zipFile
        JavaZipFileDataLoader(zipFile)
      }
      else {
        closeable = null
        ImmutableZipFileDataLoader(pool.load(jarFile), jarFile, pool)
      }

      val data = dataLoader.load(PluginManagerCore.PLUGIN_XML_PATH)
      if (data == null) {
        closeable?.close()
      }
      else {
        return Item(files = pluginJarFiles, data = data, dataLoader = dataLoader, file = jarFile)
      }
    }
  }

  if (!isBundled || isUnitTestMode) {
    val classesDir = dir.resolve("classes")
    for (candidate in arrayOf(classesDir, dir)) {
      val data = try {
        Files.readAllBytes(candidate.resolve(PluginManagerCore.PLUGIN_XML_PATH))
      }
       catch (e: NoSuchFileException) {
         null
       } ?: continue

      val jarFiles = if (pluginJarFiles.isNullOrEmpty()) {
        Collections.singletonList(classesDir)
      }
      else {
        val classPath = ArrayList<Path>(pluginJarFiles.size + 1)
        classPath.add(classesDir)
        classPath.addAll(pluginJarFiles)
        classPath
      }
      return Item(files = jarFiles, file = dir, data = data, dataLoader = LocalFsDataLoader(dir))
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
      //val start = System.currentTimeMillis()
      val descriptors = loadDescriptors(
        context = context,
        isUnitTestMode = isUnitTestMode,
        isRunningFromSources = isRunningFromSources,
        zipFilePoolDeferred = zipFilePoolDeferred,
      )

      //println("plugin descriptor loading: ${System.currentTimeMillis() - start}")
      //exitProcess(1)

      context to descriptors
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
@Internal
suspend fun loadDescriptors(
  context: DescriptorListLoadingContext,
  isUnitTestMode: Boolean = PluginManagerCore.isUnitTestMode,
  isRunningFromSources: Boolean,
  zipFilePoolDeferred: Deferred<ZipFilePool>? = null,
): PluginLoadingResult {
  val loadingResult = PluginLoadingResult()
  val buildNumber = context.productBuildNumber()
  coroutineScope {
    val zipFilePool = if (context.transient) null else zipFilePoolDeferred?.await()

    val list = async {
      collectAndSortDescriptors(loadDescriptorsFromDirs(
        context = context,
        customPluginDir = Paths.get(PathManager.getPluginsPath()),
        isUnitTestMode = isUnitTestMode,
        isRunningFromSources = isRunningFromSources,
        zipFilePool = zipFilePool,
      ))
    }

    val extraList = async {
      collectAndSortDescriptors(loadDescriptorsFromProperty(zipFilePool, context) ?: return@async emptyList())
    }

    loadingResult.addAll(descriptors = list.await(), overrideUseIfCompatible = false, productBuildNumber = buildNumber)

    // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
    loadingResult.addAll(descriptors = extraList.await(), overrideUseIfCompatible = true, productBuildNumber = buildNumber)
  }

  if (isUnitTestMode && loadingResult.enabledPluginsById.size <= 1) {
    // we're running in unit test mode, but the classpath doesn't contain any plugins; try to load bundled plugins anyway

    coroutineScope {
      val channel = Channel<IdeaPluginDescriptorImpl>(Channel.BUFFERED)
      launch {
        loadDescriptorsFromDir(dir = Paths.get(PathManager.getPreInstalledPluginsPath()),
                               context = context,
                               isBundled = true,
                               pool = if (context.transient) null else zipFilePoolDeferred?.await(),
                               isUnitTestMode = true,
                               descriptorChannel = channel)
      }.invokeOnCompletion { channel.close() }

      val list = collectAndSortDescriptors(channel)
      loadingResult.addAll(descriptors = list, overrideUseIfCompatible = false, productBuildNumber = buildNumber)
    }
  }
  return loadingResult
}

private fun CoroutineScope.loadDescriptorsFromProperty(zipFilePool: ZipFilePool?,
                                                       context: DescriptorListLoadingContext): ReceiveChannel<IdeaPluginDescriptorImpl>? {
  val pathProperty = System.getProperty("plugin.path") ?: return null

  val itemChannel = Channel<Item>(Channel.BUFFERED)
  launch(Dispatchers.IO) {
    val t = StringTokenizer(pathProperty, File.pathSeparatorChar + ",")
    while (t.hasMoreTokens()) {
      val file = Paths.get(t.nextToken())
      loadDescriptorFromFileOrDir(file = file, pool = zipFilePool, isBundled = false, isUnitTestMode = false, channel = itemChannel)
    }
  }.invokeOnCompletion { itemChannel.close() }

  // gradle-intellij-plugin heavily depends on this property in order to have core class loader plugins during tests
  val useCoreClassLoaderForPluginsFromProperty = java.lang.Boolean.getBoolean("idea.use.core.classloader.for.plugin.path")
  val descriptorChannel = Channel<IdeaPluginDescriptorImpl>(Channel.BUFFERED)
  launch {
    for (item in itemChannel) {
      launch {
        readDescriptor(item = item,
                       pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
                       parentContext = context,
                       isBundled = false,
                       isEssential = false,
                       useCoreClassLoader = useCoreClassLoaderForPluginsFromProperty)?.let {
          descriptorChannel.send(it)
        }
      }
    }
  }.invokeOnCompletion { descriptorChannel.close() }
  return descriptorChannel
}

private suspend fun collectAndSortDescriptors(channel: ReceiveChannel<IdeaPluginDescriptorImpl>): List<IdeaPluginDescriptorImpl> {
  val result = mutableListOf<IdeaPluginDescriptorImpl>()
  for (descriptor in channel) {
    result.add(descriptor)
  }
  // stable order of items in the list regardless of FS or concurrent processing
  result.sortedWith(Comparator { o1, o2 ->
    // bundled last
    when {
      o1.isBundled && !o2.isBundled -> 1
      !o1.isBundled && o2.isBundled -> -1
      else -> o1.pluginId.compareTo(o2.pluginId)
    }
  })
  return result
}

private fun CoroutineScope.loadDescriptorsFromDirs(
  context: DescriptorListLoadingContext,
  customPluginDir: Path,
  bundledPluginDir: Path? = null,
  isUnitTestMode: Boolean = PluginManagerCore.isUnitTestMode,
  isRunningFromSources: Boolean = PluginManagerCore.isRunningFromSources(),
  zipFilePool: ZipFilePool?,
): ReceiveChannel<IdeaPluginDescriptorImpl> {
  val isInDevServerMode = java.lang.Boolean.getBoolean("idea.use.dev.build.server")

  val platformPrefixProperty = PlatformUtils.getPlatformPrefix()
  val platformPrefix = if (platformPrefixProperty == PlatformUtils.QODANA_PREFIX) {
    System.getProperty("idea.parent.prefix", PlatformUtils.IDEA_PREFIX)
  }
  else {
    platformPrefixProperty
  }

  val channel = Channel<IdeaPluginDescriptorImpl>(Channel.BUFFERED)
  launch {
    loadCoreModules(context = context,
                    platformPrefix = platformPrefix,
                    isUnitTestMode = isUnitTestMode,
                    isInDevServerMode = isInDevServerMode,
                    isRunningFromSources = isRunningFromSources,
                    pool = zipFilePool,
                    descriptorChannel = channel)
    loadDescriptorsFromDir(dir = customPluginDir,
                           context = context,
                           isBundled = false,
                           isUnitTestMode = isUnitTestMode,
                           pool = zipFilePool,
                           descriptorChannel = channel)

    val effectiveBundledPluginDir = bundledPluginDir ?: if (isUnitTestMode) {
      null
    }
    else if (isInDevServerMode) {
      Paths.get(PathManager.getHomePath(), "out/dev-run", platformPrefix, "plugins")
    }
    else {
      Paths.get(PathManager.getPreInstalledPluginsPath())
    }

    if (effectiveBundledPluginDir != null) {
      loadDescriptorsFromDir(dir = effectiveBundledPluginDir,
                             context = context,
                             isBundled = true,
                             isUnitTestMode = isUnitTestMode,
                             pool = zipFilePool,
                             descriptorChannel = channel)
    }
  }.invokeOnCompletion { channel.close() }
  return channel
}

private fun CoroutineScope.loadCoreModules(
  context: DescriptorListLoadingContext,
  platformPrefix: String,
  isUnitTestMode: Boolean,
  isInDevServerMode: Boolean,
  isRunningFromSources: Boolean,
  pool: ZipFilePool?,
  descriptorChannel: SendChannel<IdeaPluginDescriptorImpl>,
) {
  val classLoader = DescriptorListLoadingContext::class.java.classLoader
  val pathResolver = ClassPathXmlPathResolver(classLoader = classLoader, isRunningFromSources = isRunningFromSources && !isInDevServerMode)
  val useCoreClassLoader = pathResolver.isRunningFromSources ||
                           platformPrefix.startsWith("CodeServer") ||
                           java.lang.Boolean.getBoolean("idea.force.use.core.classloader")
  // should be the only plugin in lib (only for Ultimate and WebStorm for now)
  if ((platformPrefix == PlatformUtils.IDEA_PREFIX || platformPrefix == PlatformUtils.WEB_PREFIX) &&
      (isInDevServerMode || (!isUnitTestMode && !isRunningFromSources))) {
    launch(Dispatchers.IO) {
      descriptorChannel.send(loadCoreProductPlugin(getResourceReader(PluginManagerCore.PLUGIN_XML_PATH, classLoader)!!,
                                                   context = context,
                                                   pathResolver = pathResolver,
                                                   useCoreClassLoader = useCoreClassLoader))
    }
  }
  else {
    launch(Dispatchers.IO) {
      getResourceReader("${PluginManagerCore.META_INF}${platformPrefix}Plugin.xml", classLoader)?.let {
        descriptorChannel.send(loadCoreProductPlugin(it, context, pathResolver, useCoreClassLoader))
      }
    }

    launch(Dispatchers.IO) {
      loadDescriptorsFromClassPath(urlToFilename = collectPluginFilesInClassPath(classLoader),
                                   context = context,
                                   pathResolver = pathResolver,
                                   useCoreClassLoader = useCoreClassLoader,
                                   pool = pool,
                                   descriptorChannel = descriptorChannel)
    }
  }
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
                                                         useCoreClassLoader = false,
                                                         isUnitTestMode = false,
                                                         pool = null)
  }
  if (descriptor != null || !file.toString().endsWith(".zip")) {
    return descriptor
  }

  val outputDir = Files.createTempDirectory("plugin")!!
  try {
    Decompressor.Zip(file).extract(outputDir)
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
                                                               useCoreClassLoader = false,
                                                               isUnitTestMode = false,
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

fun loadDescriptor(file: Path, isBundled: Boolean, pathResolver: PathResolver): IdeaPluginDescriptorImpl? {
  DescriptorListLoadingContext().use { context ->
    return runBlocking {
      loadDescriptorFromFileOrDir(
        file = file,
        context = context,
        pathResolver = pathResolver,
        isBundled = isBundled,
        isEssential = false,
        useCoreClassLoader = false,
        isUnitTestMode = false,
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
      val channel = loadDescriptorsFromDirs(
        context = context,
        customPluginDir = customPluginDir,
        bundledPluginDir = bundledPluginDir,
        zipFilePool = null,
      )

      val result = PluginLoadingResult()
      result.addAll(collectAndSortDescriptors(channel), overrideUseIfCompatible = false, productBuildNumber = context.productBuildNumber())
      result
    }
  }
}

@TestOnly
fun testLoadDescriptorsFromClassPath(loader: ClassLoader): List<IdeaPluginDescriptor> {
  return runBlocking {
    withContext(Dispatchers.Default) {
      doTestLoadDescriptorsFromClassPath(loader)
    }
  }
}

@TestOnly
private suspend fun doTestLoadDescriptorsFromClassPath(loader: ClassLoader): List<IdeaPluginDescriptor> {
  val urlToFilename = collectPluginFilesInClassPath(loader)
  val buildNumber = BuildNumber.fromString("2042.42")!!
  val context = DescriptorListLoadingContext(disabledPlugins = Collections.emptySet(),
                                             brokenPluginVersions = emptyMap(),
                                             productBuildNumber = { buildNumber })
  val channel = Channel<IdeaPluginDescriptorImpl>(Channel.BUFFERED)
  val result = PluginLoadingResult(checkModuleDependencies = false)
  coroutineScope {
    launch {
      result.addAll(collectAndSortDescriptors(channel), overrideUseIfCompatible = false, productBuildNumber = buildNumber)
    }

    launch {
      loadDescriptorsFromClassPath(
        urlToFilename = urlToFilename,
        context = context,
        pathResolver = ClassPathXmlPathResolver(loader, isRunningFromSources = false),
        useCoreClassLoader = true,
        pool = if (context.transient) null else ZipFilePool.POOL,
        descriptorChannel = channel,
      )
    }.invokeOnCompletion { channel.close() }
  }
  return result.enabledPlugins
}

private fun CoroutineScope.loadDescriptorsFromDir(
  dir: Path,
  context: DescriptorListLoadingContext,
  isBundled: Boolean,
  pool: ZipFilePool?,
  isUnitTestMode: Boolean,
  descriptorChannel: SendChannel<IdeaPluginDescriptorImpl>
) {
  val itemChannel = Channel<Item>(Channel.BUFFERED)
  launch(Dispatchers.IO) {
    collectDescriptorsFromDir(dir = dir, isBundled = isBundled, isUnitTestMode = isUnitTestMode, pool = pool, channel = itemChannel)
  }.invokeOnCompletion { itemChannel.close() }
  launch {
    for (item in itemChannel) {
      launch {
        readDescriptor(
          item = item,
          pathResolver = PluginXmlPathResolver(item.files),
          parentContext = context,
          isBundled = isBundled,
          isEssential = false,
          useCoreClassLoader = false,
        )?.let {
          descriptorChannel.send(it)
        }
      }
    }
  }
}

private fun CoroutineScope.collectDescriptorsFromDir(
  dir: Path,
  isBundled: Boolean,
  isUnitTestMode: Boolean,
  pool: ZipFilePool?,
  channel: SendChannel<Item>,
) {
  if (!Files.isDirectory(dir)) {
    return
  }

  // withContext also creates coroutineScope, so, channel.close will be closed when all tasks are finished
  Files.newDirectoryStream(dir).use { dirStream ->
    for (file in dirStream) {
      loadDescriptorFromFileOrDir(file, pool, isBundled, isUnitTestMode = isUnitTestMode, channel)
    }
  }
}

@Suppress("BlockingMethodInNonBlockingContext")
private fun CoroutineScope.loadDescriptorFromFileOrDir(file: Path,
                                                       pool: ZipFilePool?,
                                                       isBundled: Boolean,
                                                       isUnitTestMode: Boolean,
                                                       channel: SendChannel<Item>) {
  val fileName = file.fileName?.toString() ?: ""
  if (fileName.endsWith(".jar", ignoreCase = true) || fileName.endsWith(".zip", ignoreCase = true)) {
    launch {
      val dataLoader = if (pool == null) {
        JavaZipFileDataLoader(ZipFile(file.toFile(), StandardCharsets.UTF_8))
      }
      else {
        ImmutableZipFileDataLoader(pool.load(file), file, pool)
      }

      dataLoader.load(PluginManagerCore.PLUGIN_XML_PATH)?.let { data ->
        channel.send(Item(files = listOf(file), data = data, file = file, dataLoader = dataLoader))
      }
    }
  }
  else if (!fileName.startsWith('.') && !fileName.endsWith(".json") && !fileName.endsWith(".xml") && !fileName.endsWith(".etag")) {
    launch {
      if (Files.isDirectory(file)) {
        loadFromPluginDir(dir = file, isBundled = isBundled, pool = pool, isUnitTestMode = isUnitTestMode)?.let {
          channel.send(it)
        }
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
  descriptorChannel: SendChannel<IdeaPluginDescriptorImpl>,
) {
  urlToFilename.forEach { (url, filename) ->
    launch {
      loadDescriptorFromResource(resource = url,
                                 filename = filename,
                                 context = context,
                                 pathResolver = pathResolver,
                                 useCoreClassLoader = useCoreClassLoader,
                                 pool = pool)?.let {
        descriptorChannel.send(it)
      }
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
    val data: ByteArray
    when {
      URLUtil.FILE_PROTOCOL == resource.protocol -> {
        basePath = file.parent.parent
        dataLoader = LocalFsDataLoader(basePath)
        data = Files.readAllBytes(file)
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

        data = dataLoader.load("META-INF/$filename") ?: return null
      }
      else -> return null
    }

    val raw = readModuleDescriptor(input = data,
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