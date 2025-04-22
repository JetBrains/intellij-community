// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PluginDescriptorLoader")
@file:Internal

package com.intellij.ide.plugins

import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.platform.plugins.parser.impl.readBasicDescriptorData
import com.intellij.platform.util.putMoreLikelyPluginJarsFirst
import com.intellij.util.Java11Shim
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.Decompressor
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipEntryResolverPool
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import com.intellij.util.xml.dom.createXmlStreamReader
import kotlinx.coroutines.*
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.*
import java.net.URL
import java.nio.file.*
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.jar.JarInputStream
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLStreamException

private val LOG: Logger
  get() = PluginManagerCore.logger

@TestOnly
fun loadDescriptor(file: Path, loadingContext: PluginDescriptorLoadingContext, pool: ZipEntryResolverPool): IdeaPluginDescriptorImpl? {
  return loadDescriptorFromFileOrDir(file = file, loadingContext = loadingContext, pool = pool)
}

@ApiStatus.Internal
@JvmOverloads
fun loadAndInitForCoreEnv(pluginRoot: Path, fileName: String, relativeDir: String = PluginManagerCore.META_INF, id: PluginId? = null): IdeaPluginDescriptorImpl? {
  val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val initContext = ProductPluginInitContext()
  val loadingContext = PluginDescriptorLoadingContext()
  val relativePath = "${relativeDir}${fileName}"
  if (Files.isDirectory(pluginRoot)) {
    return loadDescriptorFromDir(
      dir = pluginRoot,
      loadingContext = loadingContext,
      pool = NonShareableJavaZipFilePool(),
      pathResolver = pathResolver,
      descriptorRelativePath = relativePath,
      isBundled = true,
      isEssential = true,
      id = id,
    )?.apply { initialize(context = initContext) }
  }
  else {
    return loadDescriptorFromJar(
      file = pluginRoot,
      loadingContext = loadingContext,
      pool = NonShareableJavaZipFilePool(),
      pathResolver = pathResolver,
      descriptorRelativePath = relativePath,
      isBundled = true,
      isEssential = true,
      id = id,
    )?.apply { initialize(context = initContext) }
  }
}

fun loadDescriptorFromDir(
  dir: Path,
  loadingContext: PluginDescriptorLoadingContext,
  pool: ZipEntryResolverPool,
  pathResolver: PathResolver,
  descriptorRelativePath: String = PluginManagerCore.PLUGIN_XML_PATH,
  isBundled: Boolean,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  pluginDir: Path? = null,
  id: PluginId? = null,
): IdeaPluginDescriptorImpl? {
  try {
    val dataLoader = LocalFsDataLoader(dir)
    val input = Files.newInputStream(dir.resolve(descriptorRelativePath))
    val descriptor = loadDescriptorFromStream(
      input = input,
      loadingContext = loadingContext,
      pathResolver = pathResolver,
      dataLoader = dataLoader,
      fileOrDir = dir,
      pluginDir = pluginDir,
      isBundled = isBundled,
      useCoreClassLoader = useCoreClassLoader,
      descriptorRelativePath = descriptorRelativePath,
      pool = pool,
      id = id,
    )
    descriptor.jarFiles = Collections.singletonList(dir)
    return descriptor
  }
  catch (_: NoSuchFileException) {
    return null
  }
  catch (e: Throwable) {
    if (isEssential) {
      throw e
    }
    LOG.warn("Cannot load ${dir.resolve(descriptorRelativePath)}", e)
    return null
  }
}

fun loadDescriptorFromJar(
  file: Path,
  loadingContext: PluginDescriptorLoadingContext,
  pool: ZipEntryResolverPool,
  pathResolver: PathResolver,
  descriptorRelativePath: String = PluginManagerCore.PLUGIN_XML_PATH,
  isBundled: Boolean = false,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  pluginDir: Path? = null,
  id: PluginId? = null,
): IdeaPluginDescriptorImpl? {
  var resolver: ZipEntryResolverPool.EntryResolver? = null
  try {
    resolver = pool.load(file)
    val dataLoader = ImmutableZipFileDataLoader(resolver, zipPath = file)
    val input = dataLoader.load(descriptorRelativePath, pluginDescriptorSourceOnly = true) ?: return null
    val descriptor = loadDescriptorFromStream(
      input = input,
      loadingContext = loadingContext,
      pathResolver = pathResolver,
      dataLoader = dataLoader,
      fileOrDir = file,
      pluginDir = pluginDir,
      isBundled = isBundled,
      useCoreClassLoader = useCoreClassLoader,
      descriptorRelativePath = descriptorRelativePath,
      pool = pool,
      id = id,
    )
    descriptor.jarFiles = Collections.singletonList(descriptor.pluginPath)
    return descriptor
  }
  catch (e: Throwable) {
    if (isEssential) {
      throw if (e is XMLStreamException) RuntimeException("Cannot read $file", e) else e
    }
    loadingContext.reportCannotLoad(file, e)
  }
  finally {
    resolver?.close()
  }
  return null
}

private fun loadDescriptorFromStream(
  input: InputStream,
  loadingContext: PluginDescriptorLoadingContext,
  pathResolver: PathResolver,
  dataLoader: DataLoader,
  fileOrDir: Path,
  pluginDir: Path?,
  isBundled: Boolean,
  useCoreClassLoader: Boolean,
  descriptorRelativePath: String,
  pool: ZipEntryResolverPool,
  id: PluginId? = null,
): IdeaPluginDescriptorImpl {
  val raw = PluginDescriptorFromXmlStreamConsumer(loadingContext, pathResolver.toXIncludeLoader(dataLoader)).let {
    it.consume(input, fileOrDir.toString())
    loadingContext.patchPlugin(it.getBuilder())
    if (id != null) {
      it.getBuilder().id = id.idString
    }
    it.build()
  }
  val descriptor = IdeaPluginDescriptorImpl(
    raw = raw,
    pluginPath = pluginDir ?: fileOrDir,
    isBundled = isBundled,
    useCoreClassLoader = useCoreClassLoader,
  )
  loadPluginSubDescriptors(
    descriptor = descriptor,
    pathResolver = pathResolver,
    loadingContext = loadingContext,
    dataLoader = dataLoader,
    pluginDir = pluginDir ?: fileOrDir,
    pool = pool,
  )
  return descriptor
}

@VisibleForTesting
fun loadPluginSubDescriptors(
  descriptor: IdeaPluginDescriptorImpl,
  pathResolver: PathResolver,
  loadingContext: PluginDescriptorLoadingContext,
  dataLoader: DataLoader,
  pluginDir: Path,
  pool: ZipEntryResolverPool,
) {
  val moduleDir = pluginDir.resolve("lib/modules").takeIf { Files.isDirectory(it) }
  for (module in descriptor.content.modules) {
    val subDescriptorFile = module.configFile ?: "${module.name}.xml"
    if (module.descriptorContent == null) {
      val jarFile = moduleDir?.resolve("${module.name}.jar")
      if (jarFile != null && Files.exists(jarFile)) {
        val subRaw = loadModuleFromSeparateJar(pool, jarFile, subDescriptorFile, loadingContext)
        val subDescriptor = descriptor.createSub(subRaw, subDescriptorFile, module)
        subDescriptor.jarFiles = Collections.singletonList(jarFile)
        module.descriptor = subDescriptor
      }
      else {
        val subRaw = pathResolver.resolveModuleFile(loadingContext, dataLoader, subDescriptorFile)
        val subDescriptor = descriptor.createSub(subRaw, subDescriptorFile, module)
        module.descriptor = subDescriptor
        val customRoots = pathResolver.resolveCustomModuleClassesRoots(module.name)
        if (customRoots.isNotEmpty()) {
          subDescriptor.jarFiles = customRoots
        }
      }
    }
    else {
      val subRaw = PluginDescriptorFromXmlStreamConsumer(loadingContext, null).let {
        it.consume(createXmlStreamReader(module.descriptorContent))
        it.getBuilder()
      }
      val subDescriptor = descriptor.createSub(subRaw, subDescriptorFile, module)
      if (subRaw.`package` == null || subRaw.isSeparateJar) {
        subDescriptor.jarFiles = Collections.singletonList(pluginDir.resolve("lib/modules/${module.name}.jar"))
      }
      module.descriptor = subDescriptor
    }
  }

  descriptor.loadPluginDependencyDescriptors(loadingContext = loadingContext, pathResolver = pathResolver, dataLoader = dataLoader)
}

@TestOnly
fun loadDescriptorFromFileOrDirInTests(file: Path, loadingContext: PluginDescriptorLoadingContext, isBundled: Boolean): IdeaPluginDescriptorImpl? {
  return loadDescriptorFromFileOrDir(file = file, loadingContext = loadingContext, pool = NonShareableJavaZipFilePool(), isBundled = isBundled, isEssential = true, isUnitTestMode = true)
}

@Internal
fun loadDescriptorFromFileOrDir(
  file: Path,
  loadingContext: PluginDescriptorLoadingContext,
  pool: ZipEntryResolverPool,
  pathResolver: PathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
  isBundled: Boolean = false,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  isUnitTestMode: Boolean = false,
): IdeaPluginDescriptorImpl? {
  return when {
    Files.isDirectory(file) -> {
      loadFromPluginDir(
        dir = file,
        loadingContext = loadingContext,
        pool = pool,
        pathResolver = pathResolver,
        isBundled = isBundled,
        isEssential = isEssential,
        useCoreClassLoader = useCoreClassLoader,
        isUnitTestMode = isUnitTestMode,
      )
    }
    file.toString().endsWith(".jar", ignoreCase = true) -> {
      loadDescriptorFromJar(
        file = file,
        loadingContext = loadingContext,
        pool = pool,
        pathResolver = pathResolver,
        isBundled = isBundled,
        isEssential = isEssential,
        useCoreClassLoader = useCoreClassLoader,
      )
    }
    else -> null
  }
}

/**
 * Tries to load a plugin descriptor from a given directory.
 * Lookup order:
 * * `(./lib/ *.zip or ./lib/ *.jar) / META-INF / plugin.xml`
 * * `./classes/META-INF/plugin.xml`
 * * `./META-INF/plugin.xml`
 * Libraries are sorted by a heuristic
 */
private fun loadFromPluginDir(
  dir: Path,
  loadingContext: PluginDescriptorLoadingContext,
  pool: ZipEntryResolverPool,
  pathResolver: PathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
  isBundled: Boolean = false,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  isUnitTestMode: Boolean = false,
): IdeaPluginDescriptorImpl? {
  val pluginJarFiles = resolveArchives(dir)
  if (!pluginJarFiles.isNullOrEmpty()) {
    if (pluginJarFiles.size > 1) {
      putMoreLikelyPluginJarsFirst(pluginDirName = dir.fileName.toString(), filesInLibUnderPluginDir = pluginJarFiles)
    }
    val pluginPathResolver = PluginXmlPathResolver(pluginJarFiles = pluginJarFiles, pool = pool)
    for (jarFile in pluginJarFiles) {
      loadDescriptorFromJar(
        file = jarFile,
        loadingContext = loadingContext,
        pool = pool,
        pathResolver = pluginPathResolver,
        isBundled = isBundled,
        isEssential = isEssential,
        useCoreClassLoader = useCoreClassLoader,
        pluginDir = dir,
      )?.let {
        it.jarFiles = pluginJarFiles
        return it
      }
    }
  }

  // not found, ok, let's check classes (but only for unbundled plugins)
  if (!isBundled || isUnitTestMode) {
    val classDir = dir.resolve("classes")
    sequenceOf(classDir, dir)
      .firstNotNullOfOrNull {
        loadDescriptorFromDir(
          dir = it,
          loadingContext = loadingContext,
          pool = pool,
          pathResolver = pathResolver,
          isBundled = isBundled,
          isEssential = isEssential,
          useCoreClassLoader = useCoreClassLoader,
          pluginDir = dir,
        )
      }?.let {
        if (pluginJarFiles.isNullOrEmpty()) {
          it.jarFiles = Collections.singletonList(classDir)
        }
        else {
          val classPath = ArrayList<Path>(pluginJarFiles.size + 1)
          classPath.add(classDir)
          classPath.addAll(pluginJarFiles)
          it.jarFiles = classPath
        }
        return it
      }
  }

  return null
}

/**
 * @return null if `lib` subdirectory does not exist, otherwise a list of paths to archives (.jar or .zip)
 */
private fun resolveArchives(path: Path): MutableList<Path>? {
  try {
    return Files.newDirectoryStream(path.resolve("lib")).use { stream ->
      stream.filterTo(ArrayList()) {
        val path = it.toString()
        path.endsWith(".jar", ignoreCase = true) || path.endsWith(".zip", ignoreCase = true)
      }
    }
  }
  catch (_: NotDirectoryException) {
    return null
  }
  catch (_: NoSuchFileException) {
    return null
  }
}

private fun CoroutineScope.loadDescriptorsFromProperty(loadingContext: PluginDescriptorLoadingContext, pool: ZipEntryResolverPool): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val pathProperty = System.getProperty("plugin.path") ?: return Collections.emptyList()

  // gradle-intellij-plugin heavily depends on this property to have core class loader plugins during tests
  val useCoreClassLoaderForPluginsFromProperty = java.lang.Boolean.getBoolean("idea.use.core.classloader.for.plugin.path")
  val t = StringTokenizer(pathProperty, File.pathSeparatorChar + ",")
  val list = mutableListOf<Deferred<IdeaPluginDescriptorImpl?>>()
  while (t.hasMoreTokens()) {
    val file = Paths.get(t.nextToken())
    list.add(async(Dispatchers.IO) {
      loadDescriptorFromFileOrDir(file, loadingContext, pool, useCoreClassLoader = useCoreClassLoaderForPluginsFromProperty)
    })
  }
  return list
}

suspend fun loadAndInitDescriptors(
  zipPoolDeferred: Deferred<ZipEntryResolverPool>,
  mainClassLoaderDeferred: Deferred<ClassLoader>?,
  initContext: PluginInitializationContext,
): Pair<PluginDescriptorLoadingContext, PluginLoadingResult> {
  val isUnitTestMode = PluginManagerCore.isUnitTestMode
  val isRunningFromSources = PluginManagerCore.isRunningFromSources()
  val result = PluginDescriptorLoadingContext(
    isMissingIncludeIgnored = isUnitTestMode,
    isMissingSubDescriptorIgnored = true,
    checkOptionalConfigFileUniqueness = isUnitTestMode || isRunningFromSources,
  ).use { loadingContext ->
    loadingContext to loadAndInitDescriptors(
      loadingContext = loadingContext,
      initContext = initContext,
      isUnitTestMode = isUnitTestMode,
      isRunningFromSources = isRunningFromSources,
      zipPoolDeferred = zipPoolDeferred,
      mainClassLoaderDeferred = mainClassLoaderDeferred,
    )
  }
  return result
}

@Suppress("DeferredIsResult")
internal fun CoroutineScope.scheduleLoading(
  zipPoolDeferred: Deferred<ZipEntryResolverPool>,
  mainClassLoaderDeferred: Deferred<ClassLoader>?,
  logDeferred: Deferred<Logger>?,
): Deferred<PluginSet> {
  val initContext = ProductPluginInitContext()
  val resultDeferred = async(CoroutineName("plugin descriptor loading")) {
    loadAndInitDescriptors(zipPoolDeferred, mainClassLoaderDeferred, initContext)
  }
  val pluginSetDeferred = async {
    val (loadingContext, loadingResult) = resultDeferred.await()
    PluginManagerCore.initializeAndSetPlugins(loadingContext, initContext, loadingResult)
  }
  // logging is not as a part of plugin set job for performance reasons
  launch {
    val (_, loadingResult) = resultDeferred.await()
    logPlugins(pluginSetDeferred.await().allPlugins, initContext, loadingResult, logSupplier = {
      // make sure that logger is ready to use (not a console logger)
      logDeferred?.await()
      LOG
    })
  }
  return pluginSetDeferred
}

private suspend fun logPlugins(
  plugins: Collection<IdeaPluginDescriptorImpl>,
  initContext: PluginInitializationContext,
  loadingResult: PluginLoadingResult,
  logSupplier: suspend () -> Logger,
) {
  if (AppMode.isDisableNonBundledPlugins()) {
    LOG.info("Running with disableThirdPartyPlugins argument, third-party plugins will be disabled")
  }

  val bundled = StringBuilder()
  val disabled = StringBuilder()
  val custom = StringBuilder()
  val disabledPlugins = HashSet<PluginId>()
  for (descriptor in plugins) {
    val pluginId = descriptor.pluginId
    val target = if (!PluginManagerCore.isLoaded(descriptor)) {
      if (!initContext.isPluginDisabled(pluginId)) {
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
    if (initContext.isPluginDisabled(pluginId) && !disabledPlugins.contains(pluginId)) {
      appendPlugin(descriptor, disabled)
    }
  }

  val log = logSupplier()
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

/**
 * Returns enabled plugins only.
 */
private suspend fun loadAndInitDescriptors(
  loadingContext: PluginDescriptorLoadingContext,
  initContext: PluginInitializationContext,
  isUnitTestMode: Boolean = PluginManagerCore.isUnitTestMode,
  isRunningFromSources: Boolean,
  zipPoolDeferred: Deferred<ZipEntryResolverPool>,
  mainClassLoaderDeferred: Deferred<ClassLoader>?,
): PluginLoadingResult {
  val listDeferred: List<Deferred<IdeaPluginDescriptorImpl?>>
  val extraListDeferred: List<Deferred<IdeaPluginDescriptorImpl?>>
  val zipPool = zipPoolDeferred.await()
  val mainClassLoader = mainClassLoaderDeferred?.await() ?: PluginManagerCore::class.java.classLoader
  coroutineScope {
    listDeferred = ProductLoadingStrategy.strategy.loadPluginDescriptors(
      scope = this,
      loadingContext = loadingContext,
      customPluginDir = Paths.get(PathManager.getPluginsPath()),
      bundledPluginDir = null,
      isUnitTestMode = isUnitTestMode,
      isRunningFromSources = isRunningFromSources,
      zipPool = zipPool,
      mainClassLoader = mainClassLoader,
    )
    extraListDeferred = loadDescriptorsFromProperty(loadingContext, zipPool)
  }

  val buildNumber = initContext.productBuildNumber
  val loadingResult = PluginLoadingResult()

  val isMainProcess = isMainProcess()
  loadingResult.initAndAddAll(descriptors = toSequence(listDeferred, isMainProcess), overrideUseIfCompatible = false, initContext = initContext)
  // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
  loadingResult.initAndAddAll(descriptors = toSequence(extraListDeferred, isMainProcess), overrideUseIfCompatible = true, initContext = initContext)

  if (isUnitTestMode && loadingResult.enabledPluginsById.size <= 1) {
    // we're running in unit test mode, but the classpath doesn't contain any plugins; try to load bundled plugins anyway
    loadingResult.initAndAddAll(
      descriptors = toSequence(coroutineScope {
        val dir = Paths.get(PathManager.getPreInstalledPluginsPath())
        loadDescriptorsFromDir(dir = dir, loadingContext = loadingContext, isBundled = true, pool = zipPoolDeferred.await())
      }, isMainProcess),
      overrideUseIfCompatible = false,
      initContext = initContext
    )
  }
  return loadingResult
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun toSequence(list: List<Deferred<IdeaPluginDescriptorImpl?>>, isMainProcess: Boolean?): Sequence<IdeaPluginDescriptorImpl> {
  val result = list.asSequence().mapNotNull { it.getCompleted() }
  if (isMainProcess == null) {
    return result
  }
  else {
    return result.filter { !isMainProcess || ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(it.pluginId) }
  }
}

private fun isMainProcess(): Boolean? {
  if (java.lang.Boolean.getBoolean("ide.per.project.instance")) {
    return !PathManager.getPluginsDir().fileName.toString().startsWith("perProject_")
  }
  else {
    return null
  }
}

internal fun CoroutineScope.loadPluginDescriptorsImpl(
  loadingContext: PluginDescriptorLoadingContext,
  isUnitTestMode: Boolean,
  isRunningFromSources: Boolean,
  mainClassLoader: ClassLoader,
  zipPool: ZipEntryResolverPool,
  customPluginDir: Path,
  bundledPluginDir: Path?,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val platformPrefix = PlatformUtils.getPlatformPrefix()

  val result = ArrayList<Deferred<IdeaPluginDescriptorImpl?>>()
  if (isUnitTestMode) {
    result.addAll(loadCoreModules(
      loadingContext = loadingContext,
      platformPrefix = platformPrefix,
      isUnitTestMode = true,
      isInDevServerMode = false,
      isRunningFromSources = true,
      classLoader = mainClassLoader,
      pool = zipPool,
      result = result,
    ))
    result.addAll(loadDescriptorsFromDir(dir = customPluginDir, loadingContext = loadingContext, isBundled = false, pool = zipPool))
    bundledPluginDir?.let {
      result.addAll(loadDescriptorsFromDir(dir = it, loadingContext = loadingContext, isBundled = true, pool = zipPool))
    }
    return result
  }

  val effectiveBundledPluginDir = bundledPluginDir ?: Paths.get(PathManager.getPreInstalledPluginsPath())
  val bundledPluginClasspathBytes = try {
    // use only if the format is supported (first byte it is a version)
    Files.readAllBytes(effectiveBundledPluginDir.resolve("plugin-classpath.txt")).takeIf { it[0] == 2.toByte() }
  }
  catch (_: NoSuchFileException) {
    null
  }

  if (bundledPluginClasspathBytes == null) {
    result.addAll(loadCoreModules(
      loadingContext = loadingContext,
      platformPrefix = platformPrefix,
      isUnitTestMode = false,
      isInDevServerMode = AppMode.isDevServer(),
      isRunningFromSources = isRunningFromSources,
      pool = zipPool,
      classLoader = mainClassLoader,
      result = result,
    ))
    result.addAll(loadDescriptorsFromDir(dir = customPluginDir, loadingContext = loadingContext, isBundled = false, pool = zipPool))
    result.addAll(loadDescriptorsFromDir(dir = effectiveBundledPluginDir, loadingContext = loadingContext, isBundled = true, pool = zipPool))
  }
  else {
    val byteInput = ByteArrayInputStream(bundledPluginClasspathBytes, 2, bundledPluginClasspathBytes.size)
    val input = DataInputStream(byteInput)
    val descriptorSize = input.readInt()
    val descriptorStart = bundledPluginClasspathBytes.size - byteInput.available()
    input.skipBytes(descriptorSize)
    // Gateway will be removed soon
    result.add(async {
      loadCoreProductPlugin(
        loadingContext = loadingContext,
        pathResolver = ClassPathXmlPathResolver(classLoader = mainClassLoader, isRunningFromSources = false),
        useCoreClassLoader = platformPrefix.startsWith("CodeServer") || forceUseCoreClassloader(),
        reader = if (PlatformUtils.isGateway()) {
          getResourceReader(path = PluginManagerCore.PLUGIN_XML_PATH, classLoader = mainClassLoader)!!
        }
        else {
          createXmlStreamReader(bundledPluginClasspathBytes, descriptorStart, descriptorSize)
        },
      )
    })

    result.addAll(loadDescriptorsFromDir(dir = customPluginDir, loadingContext = loadingContext, isBundled = false, pool = zipPool))

    loadFromPluginClasspathDescriptor(
      input = input,
      jarOnly = bundledPluginClasspathBytes[1] == 1.toByte(),
      loadingContext = loadingContext,
      zipPool = zipPool,
      bundledPluginDir = effectiveBundledPluginDir,
      scope = this,
      result = result,
    )
  }
  return result
}

private fun loadFromPluginClasspathDescriptor(
  input: DataInputStream,
  jarOnly: Boolean,
  loadingContext: PluginDescriptorLoadingContext,
  zipPool: ZipEntryResolverPool,
  bundledPluginDir: Path,
  scope: CoroutineScope,
  result: ArrayList<Deferred<IdeaPluginDescriptorImpl?>>,
) {
  val pluginCount = input.readUnsignedShort()
  result.ensureCapacity(result.size + pluginCount)
  repeat(pluginCount) {
    val fileCount = input.readUnsignedShort()

    val pluginDir = bundledPluginDir.resolve(input.readUTF())
    val descriptorSize = input.readInt()
    val pluginDescriptorData = ByteArray(descriptorSize).also { input.read(it) }
    val fileItems = Array(fileCount) {
      val path = input.readUTF()
      var file = pluginDir.resolve(path)
      if (!jarOnly) {
        file = file.normalize()
      }
      FileItem(file = file, path = path)
    }

    result.add(scope.async {
      try {
        loadPluginDescriptor(
          fileItems = fileItems,
          zipPool = zipPool,
          jarOnly = jarOnly,
          pluginDescriptorData = pluginDescriptorData,
          loadingContext = loadingContext,
          pluginDir = pluginDir,
        )
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        PluginManagerCore.logger.warn("Cannot load plugin descriptor, files:\n  ${fileItems.joinToString(separator = "\n  ")}", e)
        null
      }
    })
  }
}

private data class FileItem(@JvmField val file: Path, @JvmField val path: String)

private fun loadPluginDescriptor(
  fileItems: Array<FileItem>,
  zipPool: ZipEntryResolverPool,
  jarOnly: Boolean,
  pluginDescriptorData: ByteArray,
  loadingContext: PluginDescriptorLoadingContext,
  pluginDir: Path,
): IdeaPluginDescriptorImpl {
  val dataLoader = MixedDirAndJarDataLoader(fileItems, zipPool, jarOnly)
  dataLoader.use {
    return loadPluginDescriptor(
      fileItems = fileItems,
      zipPool = zipPool,
      dataLoader = dataLoader,
      pluginDescriptorData = pluginDescriptorData,
      loadingContext = loadingContext,
      pluginDir = pluginDir
    )
  }
}

private fun loadPluginDescriptor(
  fileItems: Array<FileItem>,
  zipPool: ZipEntryResolverPool,
  dataLoader: MixedDirAndJarDataLoader,
  pluginDescriptorData: ByteArray,
  loadingContext: PluginDescriptorLoadingContext,
  pluginDir: Path,
): IdeaPluginDescriptorImpl {
  val item = fileItems.first()
  val pluginPathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val descriptorInput = createNonCoalescingXmlStreamReader(input = pluginDescriptorData, locationSource = item.path)
  val raw = PluginDescriptorFromXmlStreamConsumer(loadingContext, pluginPathResolver.toXIncludeLoader(dataLoader)).let {
    it.consume(descriptorInput)
    loadingContext.patchPlugin(it.getBuilder())
    it.build()
  }
  val descriptor = IdeaPluginDescriptorImpl(raw, pluginDir, isBundled = true)
  for (module in descriptor.content.modules) {
    var classPath: List<Path>? = null
    val subDescriptorFile = module.configFile ?: "${module.name}.xml"
    val subRaw: PluginDescriptorBuilder = if (module.descriptorContent == null) {
      val input = dataLoader.load(subDescriptorFile, pluginDescriptorSourceOnly = true)
      if (input == null) {
        val jarFile = pluginDir.resolve("lib/modules/${module.name}.jar")
        classPath = Collections.singletonList(jarFile)
        loadModuleFromSeparateJar(pool = zipPool, jarFile = jarFile, subDescriptorFile = subDescriptorFile, loadingContext = loadingContext)
      }
      else {
        PluginDescriptorFromXmlStreamConsumer(loadingContext, pluginPathResolver.toXIncludeLoader(dataLoader)).let {
          it.consume(input, null)
          it.getBuilder()
        }
      }
    }
    else {
      // TODO isn't pluginPathResolver missing here?
      val subRaw = PluginDescriptorFromXmlStreamConsumer(loadingContext, null).let {
        it.consume(createXmlStreamReader(module.descriptorContent))
        it.getBuilder()
      }
      if (subRaw.`package` == null || subRaw.isSeparateJar) {
        classPath = Collections.singletonList(pluginDir.resolve("lib/modules/${module.name}.jar"))
      }
      subRaw
    }

    val subDescriptor = descriptor.createSub(subBuilder = subRaw, descriptorPath = subDescriptorFile, module = module)
    if (classPath != null) {
      subDescriptor.jarFiles = classPath
    }
    module.descriptor = subDescriptor
  }

  descriptor.loadPluginDependencyDescriptors(loadingContext = loadingContext, pathResolver = pluginPathResolver, dataLoader = dataLoader)
  descriptor.jarFiles = fileItems.map { it.file }
  return descriptor
}

private class MixedDirAndJarDataLoader(
  private val files: Array<FileItem>,
  private val pool: ZipEntryResolverPool,
  private val jarOnly: Boolean,
) : DataLoader, Closeable {
  // atomic array because it was marked volatile before
  private val resolvers = AtomicReferenceArray<ZipEntryResolverPool.EntryResolver?>(files.size)

  // loading must return a result for the sub
  override fun isExcludedFromSubSearch(jarFile: Path): Boolean = true

  override val emptyDescriptorIfCannotResolve: Boolean = true

  override fun load(path: String, pluginDescriptorSourceOnly: Boolean): InputStream? {
    val effectivePath = if (path[0] == '/') path.substring(1) else path
    for ((index, item) in files.withIndex()) {
      if (jarOnly || item.path.endsWith(".jar")) {
        if (resolvers[index] == null) { // fixme isn't it racy? might leak something
          resolvers[index] = pool.load(item.file)
        }
        val resolver = resolvers[index]!!
        val result = resolver.loadZipEntry(effectivePath)
        if (result != null) {
          return result
        }
      }
      else {
        try {
          return Files.newInputStream(item.file.resolve(effectivePath))
        }
        catch (_: NoSuchFileException) {
        }
      }
      if (jarOnly && pluginDescriptorSourceOnly) {
        break
      }
    }
    return null
  }

  override fun close() {
    for (index in files.indices) {
      resolvers.getAndSet(index, null)?.close()
    }
  }

  override fun toString(): String = "plugin-classpath.txt based data loader"
}

private fun loadModuleFromSeparateJar(
  pool: ZipEntryResolverPool,
  jarFile: Path,
  subDescriptorFile: String,
  loadingContext: PluginDescriptorLoadingContext,
): PluginDescriptorBuilder {
  val resolver = pool.load(jarFile)
  try {
    val input = resolver.loadZipEntry(subDescriptorFile) ?: throw IllegalStateException("Module descriptor $subDescriptorFile not found in $jarFile")
    // product module is always fully resolved and do not contain `xi:include`
    return PluginDescriptorFromXmlStreamConsumer(loadingContext, null).let {
      it.consume(input, jarFile.toString())
      it.getBuilder()
    }
  }
  finally {
    (resolver as? Closeable)?.close()
  }
}

private fun CoroutineScope.loadCoreModules(
  loadingContext: PluginDescriptorLoadingContext,
  platformPrefix: String,
  isUnitTestMode: Boolean,
  isInDevServerMode: Boolean,
  isRunningFromSources: Boolean,
  pool: ZipEntryResolverPool,
  classLoader: ClassLoader,
  result: MutableList<Deferred<IdeaPluginDescriptorImpl?>>,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val pathResolver = ClassPathXmlPathResolver(classLoader = classLoader, isRunningFromSources = isRunningFromSources && !isInDevServerMode)
  val useCoreClassLoader = pathResolver.isRunningFromSources || platformPrefix.startsWith("CodeServer") || forceUseCoreClassloader()
  if (loadCorePlugin(
      platformPrefix = platformPrefix,
      isInDevServerMode = isInDevServerMode,
      isUnitTestMode = isUnitTestMode,
      isRunningFromSources = isRunningFromSources,
      loadingContext = loadingContext,
      pathResolver = pathResolver,
      useCoreClassLoader = useCoreClassLoader,
      classLoader = classLoader,
      result = result,
    )) {
    return result
  }

  @Suppress("UrlHashCode")
  val urlToFilename = collectPluginFilesInClassPath(classLoader)
  if (urlToFilename.isNotEmpty()) {
    val libDir = if (useCoreClassLoader) null else Paths.get(PathManager.getLibPath())
    urlToFilename.mapTo(result) { (url, filename) ->
      async(Dispatchers.IO) {
        loadDescriptorFromResource(
          resource = url,
          filename = filename,
          loadingContext = loadingContext,
          pathResolver = pathResolver,
          useCoreClassLoader = useCoreClassLoader,
          pool = pool,
          libDir = libDir,
        )
      }
    }
  }
  return result
}

@Internal
fun CoroutineScope.loadCorePlugin(
  platformPrefix: String,
  isInDevServerMode: Boolean,
  isUnitTestMode: Boolean,
  isRunningFromSources: Boolean,
  loadingContext: PluginDescriptorLoadingContext,
  pathResolver: ClassPathXmlPathResolver,
  useCoreClassLoader: Boolean,
  classLoader: ClassLoader,
  result: MutableList<Deferred<IdeaPluginDescriptorImpl?>>,
): Boolean {
  if (isProductWithTheOnlyDescriptor(platformPrefix) && (isInDevServerMode || (!isUnitTestMode && !isRunningFromSources))) {
    result.add(async(Dispatchers.IO) {
      val reader = getResourceReader(PluginManagerCore.PLUGIN_XML_PATH, classLoader)!!
      loadCoreProductPlugin(loadingContext = loadingContext, pathResolver = pathResolver, useCoreClassLoader = useCoreClassLoader, reader = reader)
    })
    return true
  }

  result.add(async(Dispatchers.IO) {
    val path = "${PluginManagerCore.META_INF}${platformPrefix}Plugin.xml"
    val reader = getResourceReader(path, classLoader) ?: return@async null
    loadCoreProductPlugin(loadingContext = loadingContext, pathResolver = pathResolver, useCoreClassLoader = useCoreClassLoader, reader = reader)
  })
  return false
}

// should be the only plugin in lib
@Internal
fun isProductWithTheOnlyDescriptor(platformPrefix: String): Boolean {
  return platformPrefix == PlatformUtils.IDEA_PREFIX ||
         platformPrefix == PlatformUtils.WEB_PREFIX ||
         platformPrefix == PlatformUtils.DBE_PREFIX ||
         platformPrefix == PlatformUtils.GATEWAY_PREFIX ||
         platformPrefix == PlatformUtils.GITCLIENT_PREFIX
}

private fun getResourceReader(path: String, classLoader: ClassLoader): XMLStreamReader2? {
  if (classLoader is UrlClassLoader) {
    return createNonCoalescingXmlStreamReader(input = classLoader.getResourceAsBytes(path, false) ?: return null, locationSource = path)
  }
  else {
    return createNonCoalescingXmlStreamReader(input = classLoader.getResourceAsStream(path) ?: return null, locationSource = path)
  }
}

private fun loadCoreProductPlugin(
  loadingContext: PluginDescriptorLoadingContext,
  pathResolver: ClassPathXmlPathResolver,
  useCoreClassLoader: Boolean,
  reader: XMLStreamReader2,
): IdeaPluginDescriptorImpl {
  val dataLoader = object : DataLoader {
    override val emptyDescriptorIfCannotResolve: Boolean = true

    override fun load(path: String, pluginDescriptorSourceOnly: Boolean) = throw IllegalStateException("must be not called")

    override fun toString() = "product classpath"
  }
  val raw = PluginDescriptorFromXmlStreamConsumer(loadingContext, pathResolver.toXIncludeLoader(dataLoader)).let {
    it.consume(reader)
    loadingContext.patchPlugin(it.getBuilder())
    it.build()
  }
  val libDir = Paths.get(PathManager.getLibPath())
  val descriptor = IdeaPluginDescriptorImpl(raw = raw, pluginPath = libDir, isBundled = true, useCoreClassLoader = useCoreClassLoader)
  loadContentModuleDescriptors(descriptor = descriptor, pathResolver = pathResolver, libDir = libDir, loadingContext = loadingContext, dataLoader = dataLoader)
  descriptor.loadPluginDependencyDescriptors(loadingContext = loadingContext, pathResolver = pathResolver, dataLoader = dataLoader)
  return descriptor
}

private fun loadContentModuleDescriptors(
  descriptor: IdeaPluginDescriptorImpl,
  pathResolver: ClassPathXmlPathResolver,
  libDir: Path,
  loadingContext: PluginDescriptorLoadingContext,
  dataLoader: DataLoader,
) {
  val moduleDir = libDir.resolve("modules")
  val moduleDirExists = Files.isDirectory(moduleDir)
  val loadingStrategy = ProductLoadingStrategy.strategy

  for (module in descriptor.content.modules) {
    check(module.configFile == null) {
      "product module must not use `/` notation for module descriptor file (configFile=${module.configFile})"
    }

    val moduleName = module.name
    val subDescriptorFile = "$moduleName.xml"

    if (moduleDirExists &&
        !pathResolver.isRunningFromSources && moduleName.startsWith("intellij.") &&
        loadProductModule(
          loadingStrategy = loadingStrategy,
          moduleDir = moduleDir,
          module = module,
          subDescriptorFile = subDescriptorFile,
          loadingContext = loadingContext,
          pathResolver = pathResolver,
          dataLoader = dataLoader,
          containerDescriptor = descriptor,
        )) {
      continue
    }

    val raw = pathResolver.resolveModuleFile(readContext = loadingContext, dataLoader = dataLoader, path = subDescriptorFile)
    val subDescriptor = descriptor.createSub(subBuilder = raw, descriptorPath = subDescriptorFile, module = module)
    val customModuleClassesRoots = pathResolver.resolveCustomModuleClassesRoots(moduleName)
    if (customModuleClassesRoots.isNotEmpty()) {
      subDescriptor.jarFiles = customModuleClassesRoots
    }
    module.descriptor = subDescriptor
  }
}

private fun loadProductModule(
  loadingStrategy: ProductLoadingStrategy,
  moduleDir: Path,
  module: PluginContentDescriptor.ModuleItem,
  subDescriptorFile: String,
  loadingContext: PluginDescriptorLoadingContext,
  pathResolver: ClassPathXmlPathResolver,
  dataLoader: DataLoader,
  containerDescriptor: IdeaPluginDescriptorImpl,
): Boolean {
  val moduleName = module.name
  val jarFile = loadingStrategy.findProductContentModuleClassesRoot(moduleName, moduleDir)
  val moduleRaw: PluginDescriptorBuilder = if (jarFile == null) {
    // do not log - the severity of the error is determined by the loadingStrategy, the default strategy does not return null at all
    PluginDescriptorBuilder.builder().apply {
      `package` = "unresolved.$moduleName"
    }
  }
  else {
    val reader = createXmlStreamReader(requireNotNull(module.descriptorContent) {
      "Product module ${module.name} descriptor content is not embedded - corrupted distribution " +
      "(jarFile=$jarFile, containerDescriptor=$containerDescriptor, siblings=${containerDescriptor.content.modules.joinToString()})"
    })
    PluginDescriptorFromXmlStreamConsumer(loadingContext, pathResolver.toXIncludeLoader(dataLoader)).let {
      it.consume(reader)
      it.getBuilder()
    }
  }
  val subDescriptor = containerDescriptor.createSub(moduleRaw, subDescriptorFile, module)
  subDescriptor.jarFiles = jarFile?.let { Java11Shim.INSTANCE.listOf(it) } ?: Java11Shim.INSTANCE.listOf()
  module.descriptor = subDescriptor
  return true
}

@Suppress("UrlHashCode")
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
fun loadAndInitDescriptorFromArtifact(file: Path, buildNumber: BuildNumber?): IdeaPluginDescriptorImpl? {
  val initContext = ProductPluginInitContext(buildNumberOverride = buildNumber)
  val loadingContext = PluginDescriptorLoadingContext(
    getBuildNumberForDefaultDescriptorVersion = { buildNumber ?: PluginManagerCore.buildNumber },
    isMissingSubDescriptorIgnored = true,
  )

  val path = file.toString()
  if (path.endsWith(".jar", ignoreCase = true)) {
    val descriptor = loadDescriptorFromJar(
      file = file,
      loadingContext = loadingContext,
      pool = NonShareableJavaZipFilePool(),
      pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
    )?.apply { initialize(context = initContext) }
    if (descriptor != null) {
      return descriptor
    }
  }

  if (!path.endsWith(".zip", ignoreCase = true)) {
    return null
  }

  val outputDir = Files.createTempDirectory("plugin")!!
  try {
    Decompressor.Zip(file)
      .withZipExtensions()
      .extract(outputDir)
    try {
      //org.jetbrains.intellij.build.io.ZipArchiveOutputStream may add __index__ entry to the plugin zip, we need to ignore it here
      val rootDir = NioFiles.list(outputDir).firstOrNull { it.fileName.toString() != "__index__" }
      if (rootDir != null) {
        @Suppress("SSBasedInspection")
        return runBlocking {
          loadFromPluginDir(dir = rootDir, loadingContext = loadingContext, pool = NonShareableJavaZipFilePool(), isUnitTestMode = PluginManagerCore.isUnitTestMode)
            ?.apply { initialize(context = initContext) }
        }
      }
    }
    catch (_: NoSuchFileException) {
    }
  }
  finally {
    NioFiles.deleteRecursively(outputDir)
  }

  return null
}

fun loadDescriptor(file: Path, isBundled: Boolean, pathResolver: PathResolver): IdeaPluginDescriptorImpl? {
  PluginDescriptorLoadingContext().use { loadingContext ->
    return loadDescriptorFromFileOrDir(file = file, loadingContext = loadingContext, pool = NonShareableJavaZipFilePool(), pathResolver = pathResolver, isBundled = isBundled)
  }
}

@Throws(ExecutionException::class, InterruptedException::class, IOException::class)
fun loadAndInitDescriptorsFromOtherIde(
  customPluginDir: Path,
  bundledPluginDir: Path?,
  brokenPluginVersions: Map<PluginId, Set<String>>?,
  productBuildNumber: BuildNumber?,
): PluginLoadingResult {
  val initContext = ProductPluginInitContext(
    buildNumberOverride = productBuildNumber,
    disabledPluginsOverride = emptySet(),
    expiredPluginsOverride = emptySet(),
    brokenPluginVersionsOverride = brokenPluginVersions,
  )
  return PluginDescriptorLoadingContext(
    getBuildNumberForDefaultDescriptorVersion = { productBuildNumber ?: PluginManagerCore.buildNumber },
    isMissingIncludeIgnored = true,
    isMissingSubDescriptorIgnored = true,
  ).use { loadingContext ->
    val result = PluginLoadingResult()
    @Suppress("SSBasedInspection")
    result.initAndAddAll(
      descriptors = toSequence(runBlocking {
        val classLoader = PluginDescriptorLoadingContext::class.java.classLoader
        val pool = NonShareableJavaZipFilePool()
        loadPluginDescriptorsImpl(
          loadingContext = loadingContext,
          isUnitTestMode = PluginManagerCore.isUnitTestMode,
          isRunningFromSources = PluginManagerCore.isRunningFromSources(),
          mainClassLoader = classLoader,
          zipPool = pool,
          customPluginDir = customPluginDir,
          bundledPluginDir = bundledPluginDir,
        )
      }, isMainProcess()),
      overrideUseIfCompatible = false,
      initContext = initContext
    )
    result
  }
}

suspend fun loadDescriptorsFromCustomPluginDir(customPluginDir: Path, ignoreCompatibility: Boolean = false): PluginLoadingResult {
  val initContext = ProductPluginInitContext()
  return PluginDescriptorLoadingContext(isMissingIncludeIgnored = true, isMissingSubDescriptorIgnored = true).use { loadingContext ->
    val result = PluginLoadingResult()
    result.initAndAddAll(
      descriptors = toSequence(
        list = coroutineScope {
          loadDescriptorsFromDir(dir = customPluginDir, loadingContext = loadingContext, isBundled = ignoreCompatibility, pool = NonShareableJavaZipFilePool())
        },
        isMainProcess = isMainProcess(),
      ),
      overrideUseIfCompatible = false,
      initContext = initContext
    )
    result
  }
}

@TestOnly
@JvmOverloads
fun loadAndInitDescriptorsFromClassPathInTest(
  loader: ClassLoader,
  zipPool: ZipEntryResolverPool = NonShareableJavaZipFilePool(),
): List<IdeaPluginDescriptor> {
  @Suppress("UrlHashCode")
  val urlToFilename = collectPluginFilesInClassPath(loader)
  val buildNumber = BuildNumber.fromString("2042.42")!!
  val initContext = PluginInitializationContext.build(
    disabledPlugins = emptySet(),
    expiredPlugins = emptySet(),
    brokenPluginVersions = emptyMap(),
    getProductBuildNumber = { buildNumber },
  )
  val loadingContext = PluginDescriptorLoadingContext(
    getBuildNumberForDefaultDescriptorVersion = { buildNumber },
  )
  val result = PluginLoadingResult(checkModuleDependencies = false)
  result.initAndAddAll(
    descriptors = toSequence(
      list = @Suppress("RAW_RUN_BLOCKING") runBlocking {
        urlToFilename.map { (url, filename) ->
          async(Dispatchers.IO) {
            loadDescriptorFromResource(
              resource = url,
              filename = filename,
              loadingContext = loadingContext,
              pathResolver = ClassPathXmlPathResolver(classLoader = loader, isRunningFromSources = false),
              useCoreClassLoader = true,
              pool = zipPool,
              libDir = null,
            )
          }
        }
      },
      isMainProcess = isMainProcess(),
    ),
    overrideUseIfCompatible = false,
    initContext = initContext
  )
  return result.enabledPlugins
}

// do not use it
@Internal
fun loadCustomDescriptorsFromDirForImportSettings(scope: CoroutineScope, dir: Path, context: PluginDescriptorLoadingContext): List<Deferred<IdeaPluginDescriptorImpl?>> {
  return scope.loadDescriptorsFromDir(dir = dir, loadingContext = context, isBundled = false, pool = NonShareableJavaZipFilePool())
}

internal fun CoroutineScope.loadDescriptorsFromDir(
  dir: Path,
  loadingContext: PluginDescriptorLoadingContext,
  isBundled: Boolean,
  pool: ZipEntryResolverPool,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  if (!Files.isDirectory(dir)) {
    return Collections.emptyList()
  }
  else {
    return Files.newDirectoryStream(dir).use { dirStream ->
      dirStream.map { file ->
        async(Dispatchers.IO) {
          loadDescriptorFromFileOrDir(file = file, loadingContext = loadingContext, pool = pool, isBundled = isBundled)
        }
      }
    }
  }
}

// filename - plugin.xml or ${platformPrefix}Plugin.xml
private fun loadDescriptorFromResource(
  resource: URL,
  filename: String,
  loadingContext: PluginDescriptorLoadingContext,
  pathResolver: ClassPathXmlPathResolver,
  useCoreClassLoader: Boolean,
  pool: ZipEntryResolverPool,
  libDir: Path?,
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
        val resolver = pool.load(file)
        closeable = resolver as? Closeable
        val loader = ImmutableZipFileDataLoader(resolver = resolver, zipPath = file)

        val relevantJarsRoot = PathManager.getArchivedCompliedClassesLocation()
        if (relevantJarsRoot != null && file.startsWith(relevantJarsRoot)) {
          // support for archived compile outputs (each module in separate jar)
          basePath = file.parent
          dataLoader = object : DataLoader by loader {
            // should be similar as in LocalFsDataLoader
            override val emptyDescriptorIfCannotResolve = true
          }
        }
        else {
          // support for unpacked plugins in classpath, e.g. .../community/build/dependencies/build/kotlin/Kotlin/lib/kotlin-plugin.jar
          basePath = file.parent?.takeIf { it.endsWith("lib") }?.parent ?: file
          dataLoader = loader
        }

        input = dataLoader.load("META-INF/$filename", pluginDescriptorSourceOnly = true) ?: return null
      }
      else -> return null
    }

    val raw = PluginDescriptorFromXmlStreamConsumer(loadingContext, pathResolver.toXIncludeLoader(dataLoader)).let {
      it.consume(input, file.toString())
      loadingContext.patchPlugin(it.getBuilder())
      it.build()
    }
    // it is very important to not set `useCoreClassLoader = true` blindly
    // - product modules must use their own class loader if not running from sources
    val descriptor = IdeaPluginDescriptorImpl(raw = raw, pluginPath = basePath, isBundled = true, useCoreClassLoader = useCoreClassLoader)

    if (libDir == null) {
      val runFromSources = pathResolver.isRunningFromSources || PluginManagerCore.isUnitTestMode || forceUseCoreClassloader()
      for (module in descriptor.content.modules) {
        val subDescriptorFile = module.configFile ?: "${module.name}.xml"
        val subRaw = pathResolver.resolveModuleFile(loadingContext, dataLoader, subDescriptorFile)
        val subDescriptor = descriptor.createSub(subRaw, subDescriptorFile, module)
        if (runFromSources && subDescriptor.packagePrefix == null) {
          // no package in run from sources - load module from main classpath
          subDescriptor.jarFiles = Collections.emptyList()
        }
        module.descriptor = subDescriptor
      }
    }
    else {
      loadContentModuleDescriptors(descriptor = descriptor, pathResolver = pathResolver, libDir = libDir, loadingContext = loadingContext, dataLoader = dataLoader)
    }
    descriptor.loadPluginDependencyDescriptors(loadingContext = loadingContext, pathResolver = pathResolver, dataLoader = dataLoader)
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

private fun forceUseCoreClassloader() = java.lang.Boolean.getBoolean("idea.force.use.core.classloader")

/** Unlike [readBasicDescriptorDataFromArtifact], this method loads only basic data (plugin ID, name, etc.) */
@Throws(IOException::class)
@RequiresBackgroundThread
fun readBasicDescriptorDataFromArtifact(file: Path): IdeaPluginDescriptorImpl? {
  val fileName = file.fileName.toString()
  if (fileName.endsWith(".jar", ignoreCase = true)) {
    Files.newInputStream(file).use { stream ->
      return readDescriptorFromJarStream(stream, file)
    }
  }
  else if (fileName.endsWith(".zip", ignoreCase = true)) {
    ZipInputStream(Files.newInputStream(file)).use { stream ->
      val pattern = Regex("[^/]+/lib/[^/]+\\.jar")
      while (true) {
        val entry = stream.nextEntry ?: break
        if (entry.name.matches(pattern)) {
          val descriptor = readDescriptorFromJarStream(stream, file)
          if (descriptor != null) {
            return descriptor
          }
        }
      }
    }
  }
  return null
}

@Throws(IOException::class)
private fun readDescriptorFromJarStream(input: InputStream, path: Path): IdeaPluginDescriptorImpl? {
  val stream = JarInputStream(input)
  while (true) {
    val entry = stream.nextJarEntry ?: break
    if (entry.name == PluginManagerCore.PLUGIN_XML_PATH) {
      try {
        val raw = readBasicDescriptorData(stream)
        if (raw != null) {
          return IdeaPluginDescriptorImpl(raw = raw, pluginPath = path, isBundled = false)
        }
      }
      catch (e: XMLStreamException) {
        throw IOException(e)
      }
    }
  }
  return null
}
