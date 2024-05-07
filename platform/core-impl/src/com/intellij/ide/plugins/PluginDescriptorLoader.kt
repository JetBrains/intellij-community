// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty", "ReplacePutWithAssignment", "RAW_RUN_BLOCKING", "LiftReturnOrAssignment",
               "UrlHashCode")
@file:JvmName("PluginDescriptorLoader")
@file:Internal

package com.intellij.ide.plugins

import com.intellij.ide.plugins.PluginManagerCore.isUnitTestMode
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.util.putMoreLikelyPluginJarsFirst
import com.intellij.util.Java11Shim
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.io.Decompressor
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import com.intellij.util.xml.dom.createXmlStreamReader
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

private val LOG: Logger
  get() = PluginManagerCore.logger

@TestOnly
fun loadDescriptor(file: Path, parentContext: DescriptorListLoadingContext): IdeaPluginDescriptorImpl? {
  return loadDescriptorFromFileOrDir(file = file, context = parentContext, pool = NonShareableJavaZipFilePool())
}

internal fun loadForCoreEnv(pluginRoot: Path, fileName: String): IdeaPluginDescriptorImpl? {
  val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val parentContext = DescriptorListLoadingContext()
  if (Files.isDirectory(pluginRoot)) {
    return loadDescriptorFromDir(
      dir = pluginRoot,
      descriptorRelativePath = "${PluginManagerCore.META_INF}$fileName",
      pluginDir = null,
      context = parentContext,
      pathResolver = pathResolver,
      isBundled = true,
      isEssential = true,
    )
  }
  else {
    return runBlocking {
      loadDescriptorFromJar(
        file = pluginRoot,
        descriptorRelativePath = "META-INF/$fileName",
        pathResolver = pathResolver,
        parentContext = parentContext,
        isBundled = true,
        isEssential = true,
        pluginDir = null,
        useCoreClassLoader = false,
        pool = NonShareableJavaZipFilePool(),
      )
    }
  }
}

fun loadDescriptorFromDir(
  dir: Path,
  pluginDir: Path?,
  context: DescriptorListLoadingContext,
  pathResolver: PathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
  descriptorRelativePath: String = PluginManagerCore.PLUGIN_XML_PATH,
  isBundled: Boolean,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
): IdeaPluginDescriptorImpl? {
  try {
    val dataLoader = LocalFsDataLoader(dir)
    val raw = readModuleDescriptor(
      input = Files.newInputStream(dir.resolve(descriptorRelativePath)),
      readContext = context,
      pathResolver = pathResolver,
      dataLoader = dataLoader,
      includeBase = null,
      readInto = null,
      locationSource = dir.toString(),
    )
    val descriptor = IdeaPluginDescriptorImpl(
      raw = raw,
      path = pluginDir ?: dir,
      isBundled = isBundled,
      id = null,
      moduleName = null,
      useCoreClassLoader = useCoreClassLoader,
    )
    context.debugData?.recordDescriptorPath(descriptor = descriptor, rawPluginDescriptor = raw, path = descriptorRelativePath)
    descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = context, dataLoader = dataLoader)
    descriptor.jarFiles = Collections.singletonList(dir)
    return descriptor
  }
  catch (e: NoSuchFileException) {
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
  descriptorRelativePath: String = PluginManagerCore.PLUGIN_XML_PATH,
  pathResolver: PathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
  parentContext: DescriptorListLoadingContext,
  isBundled: Boolean = false,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  pluginDir: Path?,
  pool: ZipFilePool,
  customDataLoader: DataLoader? = null,
): IdeaPluginDescriptorImpl? {
  var closeable: Closeable? = null
  try {
    val dataLoader = if (customDataLoader == null) {
      val resolver = pool.load(file)
      closeable = resolver as? Closeable
      ImmutableZipFileDataLoader(resolver = resolver, zipPath = file)
    }
    else {
      customDataLoader
    }

    val raw = readModuleDescriptor(
      input = dataLoader.load(descriptorRelativePath, pluginDescriptorSourceOnly = true) ?: return null,
      readContext = parentContext,
      pathResolver = pathResolver,
      dataLoader = dataLoader,
      includeBase = null,
      readInto = null,
      locationSource = file.toString(),
    )

    val descriptor = IdeaPluginDescriptorImpl(
      raw = raw,
      path = pluginDir ?: file,
      isBundled = isBundled,
      id = null,
      moduleName = null,
      useCoreClassLoader = useCoreClassLoader,
    )
    parentContext.debugData?.recordDescriptorPath(descriptor, raw, descriptorRelativePath)
    descriptor.readExternal(raw = raw, pathResolver = pathResolver, context = parentContext, dataLoader = dataLoader)
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

@VisibleForTesting
fun loadDescriptorFromFileOrDirInTests(
  file: Path,
  context: DescriptorListLoadingContext,
  isBundled: Boolean,
): IdeaPluginDescriptorImpl? {
  return loadDescriptorFromFileOrDir(
    file = file,
    context = context,
    isBundled = isBundled,
    isEssential = true,
    isUnitTestMode = true,
    pool = NonShareableJavaZipFilePool(),
  )
}

@Internal
fun loadDescriptorFromFileOrDir(
  file: Path,
  context: DescriptorListLoadingContext,
  pathResolver: PathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
  isBundled: Boolean = false,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  isUnitTestMode: Boolean = false,
  pool: ZipFilePool,
): IdeaPluginDescriptorImpl? {
  if (Files.isDirectory(file)) {
    return loadFromPluginDir(
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
  else if (file.toString().endsWith(".jar", ignoreCase = true)) {
    return loadDescriptorFromJar(
      file = file,
      descriptorRelativePath = PluginManagerCore.PLUGIN_XML_PATH,
      pathResolver = pathResolver,
      parentContext = context,
      isBundled = isBundled,
      isEssential = isEssential,
      pluginDir = null,
      useCoreClassLoader = useCoreClassLoader,
      pool = pool,
    )
  }
  else {
    return null
  }
}

// [META-INF] [classes] lib/*.jar
private fun loadFromPluginDir(
  file: Path,
  parentContext: DescriptorListLoadingContext,
  isBundled: Boolean = false,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  pathResolver: PathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
  isUnitTestMode: Boolean = false,
  pool: ZipFilePool,
): IdeaPluginDescriptorImpl? {
  val pluginJarFiles = resolveArchives(file)
  if (!pluginJarFiles.isNullOrEmpty()) {
    if (pluginJarFiles.size > 1) {
      putMoreLikelyPluginJarsFirst(pluginDirName = file.fileName.toString(), filesInLibUnderPluginDir = pluginJarFiles)
    }
    val pluginPathResolver = PluginXmlPathResolver(pluginJarFiles = pluginJarFiles, pool = pool)
    for (jarFile in pluginJarFiles) {
      loadDescriptorFromJar(
        file = jarFile,
        descriptorRelativePath = PluginManagerCore.PLUGIN_XML_PATH,
        pathResolver = pluginPathResolver,
        parentContext = parentContext,
        isBundled = isBundled,
        isEssential = isEssential,
        pluginDir = file,
        useCoreClassLoader = useCoreClassLoader,
        pool = pool,
      )?.let {
        it.jarFiles = pluginJarFiles
        return it
      }
    }
  }

  // not found, ok, let's check classes (but only for unbundled plugins)
  if (!isBundled || isUnitTestMode) {
    val classDir = file.resolve("classes")
    sequenceOf(classDir, file)
      .firstNotNullOfOrNull {
        loadDescriptorFromDir(
          dir = it,
          pluginDir = file,
          context = parentContext,
          isBundled = isBundled,
          isEssential = isEssential,
          pathResolver = pathResolver,
          useCoreClassLoader = useCoreClassLoader,
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

private fun CoroutineScope.loadDescriptorsFromProperty(
  context: DescriptorListLoadingContext,
  pool: ZipFilePool,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val pathProperty = System.getProperty("plugin.path") ?: return Collections.emptyList()

  // gradle-intellij-plugin heavily depends on this property in order to have core class loader plugins during tests
  val useCoreClassLoaderForPluginsFromProperty = java.lang.Boolean.getBoolean("idea.use.core.classloader.for.plugin.path")
  val t = StringTokenizer(pathProperty, File.pathSeparatorChar + ",")
  val list = mutableListOf<Deferred<IdeaPluginDescriptorImpl?>>()
  while (t.hasMoreTokens()) {
    val file = Paths.get(t.nextToken())
    list.add(async(Dispatchers.IO) {
      loadDescriptorFromFileOrDir(
        file = file,
        context = context,
        useCoreClassLoader = useCoreClassLoaderForPluginsFromProperty,
        pool = pool,
      )
    })
  }
  return list
}

@Suppress("DeferredIsResult")
internal fun CoroutineScope.scheduleLoading(
  zipFilePoolDeferred: Deferred<ZipFilePool>,
  mainClassLoaderDeferred: Deferred<ClassLoader>?,
  logDeferred: Deferred<Logger>?,
): Deferred<PluginSet> {
  val resultDeferred = async(CoroutineName("plugin descriptor loading")) {
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
        mainClassLoaderDeferred = mainClassLoaderDeferred
      )
    }
    result
  }
  val pluginSetDeferred = async {
    val pair = resultDeferred.await()
    PluginManagerCore.initializeAndSetPlugins(context = pair.first, loadingResult = pair.second)
  }

  // logging is not as a part of plugin set job for performance reasons
  launch {
    val pair = resultDeferred.await()
    logPlugins(plugins = pluginSetDeferred.await().allPlugins,
               context = pair.first,
               loadingResult = pair.second,
               logSupplier = {
                 // make sure that logger is ready to use (not a console logger)
                 logDeferred?.await()
                 LOG
               })
  }
  return pluginSetDeferred
}

private suspend fun logPlugins(plugins: Collection<IdeaPluginDescriptorImpl>,
                               context: DescriptorListLoadingContext,
                               loadingResult: PluginLoadingResult,
                               logSupplier: suspend () -> Logger) {
  if (AppMode.isDisableNonBundledPlugins()) {
    LOG.info("Running with disableThirdPartyPlugins argument, third-party plugins will be disabled")
  }

  val bundled = StringBuilder()
  val disabled = StringBuilder()
  val custom = StringBuilder()
  val disabledPlugins = HashSet<PluginId>()
  for (descriptor in plugins) {
    val pluginId = descriptor.pluginId
    val target = if (!descriptor.isEnabled) {
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

internal class NonShareableJavaZipFilePool : ZipFilePool() {
  override fun load(file: Path): EntryResolver {
    val zipFile = ZipFile(file.toFile(), StandardCharsets.UTF_8)
    return object : EntryResolver, Closeable {
      override fun loadZipEntry(path: String): InputStream? {
        val entry = zipFile.getEntry(if (path[0] == '/') path.substring(1) else path) ?: return null
        return zipFile.getInputStream(entry)
      }

      override fun close() {
        zipFile.close()
      }
    }
  }

  override fun loadZipFile(file: Path): Any {
    throw IllegalStateException("Should not be called")
  }
}

/**
 * Returns enabled plugins only.
 */
private suspend fun loadDescriptors(
  context: DescriptorListLoadingContext,
  isUnitTestMode: Boolean = PluginManagerCore.isUnitTestMode,
  isRunningFromSources: Boolean,
  zipFilePoolDeferred: Deferred<ZipFilePool>,
  mainClassLoaderDeferred: Deferred<ClassLoader>?,
): PluginLoadingResult {
  val listDeferred: List<Deferred<IdeaPluginDescriptorImpl?>>
  val extraListDeferred: List<Deferred<IdeaPluginDescriptorImpl?>>
  val zipFilePool = zipFilePoolDeferred.await()
  val mainClassLoader = mainClassLoaderDeferred?.await() ?: PluginManagerCore::class.java.classLoader
  coroutineScope {
    listDeferred = loadDescriptorsFromDirs(
      context = context,
      customPluginDir = Paths.get(PathManager.getPluginsPath()),
      isUnitTestMode = isUnitTestMode,
      isRunningFromSources = isRunningFromSources,
      zipFilePool = zipFilePool,
      bundledPluginDir = null,
      mainClassLoader = mainClassLoader,
      loadingStrategy = ProductLoadingStrategy.strategy
    )
    extraListDeferred = loadDescriptorsFromProperty(context = context, pool = zipFilePool)
  }

  val buildNumber = context.productBuildNumber()
  val loadingResult = PluginLoadingResult()

  val isMainProcess = isMainProcess()
  loadingResult.addAll(
    descriptors = toSequence(listDeferred, isMainProcess = isMainProcess),
    overrideUseIfCompatible = false,
    productBuildNumber = buildNumber,
  )
  // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
  loadingResult.addAll(descriptors = toSequence(extraListDeferred, isMainProcess = isMainProcess),
                       overrideUseIfCompatible = true,
                       productBuildNumber = buildNumber)

  if (isUnitTestMode && loadingResult.enabledPluginsById.size <= 1) {
    // we're running in unit test mode, but the classpath doesn't contain any plugins; try to load bundled plugins anyway
    loadingResult.addAll(
      descriptors = toSequence(coroutineScope {
        loadDescriptorsFromDir(
          dir = Paths.get(PathManager.getPreInstalledPluginsPath()),
          context = context,
          isBundled = true,
          pool = zipFilePoolDeferred.await()
        )
      }, isMainProcess = isMainProcess),
      overrideUseIfCompatible = false,
      productBuildNumber = buildNumber,
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
  if (!java.lang.Boolean.getBoolean("ide.per.project.instance")) {
    return null
  }
  return !PathManager.getPluginsDir().fileName.toString().startsWith("perProject_")
}

private fun CoroutineScope.loadDescriptorsFromDirs(
  context: DescriptorListLoadingContext,
  customPluginDir: Path,
  bundledPluginDir: Path?,
  isUnitTestMode: Boolean = PluginManagerCore.isUnitTestMode,
  isRunningFromSources: Boolean = PluginManagerCore.isRunningFromSources(),
  zipFilePool: ZipFilePool,
  mainClassLoader: ClassLoader,
  loadingStrategy: ProductLoadingStrategy,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val platformPrefixProperty = PlatformUtils.getPlatformPrefix()
  val platformPrefix = if (platformPrefixProperty == PlatformUtils.QODANA_PREFIX) {
    System.getProperty("idea.parent.prefix", PlatformUtils.IDEA_PREFIX)
  }
  else {
    platformPrefixProperty
  }

  val result = ArrayList<Deferred<IdeaPluginDescriptorImpl?>>()
  result.addAll(loadCoreModules(
    context = context,
    platformPrefix = platformPrefix,
    isUnitTestMode = isUnitTestMode,
    isInDevServerMode = AppMode.isDevServer(),
    isRunningFromSources = isRunningFromSources,
    pool = zipFilePool,
    classLoader = mainClassLoader,
  ))
  result.addAll(loadingStrategy.loadCustomPluginDescriptors(
    scope = this,
    customPluginDir = customPluginDir,
    context = context,
    zipFilePool = zipFilePool,
  ))

  result.addAll(loadingStrategy.loadBundledPluginDescriptors(
    scope = this,
    bundledPluginDir = bundledPluginDir,
    isUnitTestMode = isUnitTestMode,
    context = context,
    zipFilePool = zipFilePool,
  ))
  return result
}

private fun CoroutineScope.loadCoreModules(
  context: DescriptorListLoadingContext,
  platformPrefix: String,
  isUnitTestMode: Boolean,
  isInDevServerMode: Boolean,
  isRunningFromSources: Boolean,
  pool: ZipFilePool,
  classLoader: ClassLoader,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val pathResolver = ClassPathXmlPathResolver(classLoader = classLoader, isRunningFromSources = isRunningFromSources && !isInDevServerMode)
  val useCoreClassLoader = pathResolver.isRunningFromSources ||
                           platformPrefix.startsWith("CodeServer") ||
                           java.lang.Boolean.getBoolean("idea.force.use.core.classloader")
  if ((isProductWithTheOnlyDescriptor(platformPrefix)) && (isInDevServerMode || (!isUnitTestMode && !isRunningFromSources))) {
    return Java11Shim.INSTANCE.listOf(async(Dispatchers.IO) {
      loadCoreProductPlugin(
        path = PluginManagerCore.PLUGIN_XML_PATH,
        context = context,
        pathResolver = pathResolver,
        useCoreClassLoader = useCoreClassLoader,
        reader = getResourceReader(PluginManagerCore.PLUGIN_XML_PATH, classLoader)!!,
      )
    })
  }

  val fileName = "${platformPrefix}Plugin.xml"
  val result = mutableListOf<Deferred<IdeaPluginDescriptorImpl?>>()
  result.add(async(Dispatchers.IO) {
    val path = "${PluginManagerCore.META_INF}$fileName"
    loadCoreProductPlugin(
      path = path,
      context = context,
      pathResolver = pathResolver,
      useCoreClassLoader = useCoreClassLoader,
      reader = getResourceReader(path, classLoader) ?: return@async null,
    )
  })

  if (ProductLoadingStrategy.strategy.shouldLoadDescriptorsFromCoreClassPath) {
    val urlToFilename = collectPluginFilesInClassPath(classLoader)
    if (urlToFilename.isNotEmpty()) {
      val libDir = if (useCoreClassLoader) null else Paths.get(PathManager.getLibPath())
      urlToFilename.mapTo(result) { (url, filename) ->
        async(Dispatchers.IO) {
          loadDescriptorFromResource(
            resource = url,
            filename = filename,
            context = context,
            pathResolver = pathResolver,
            useCoreClassLoader = useCoreClassLoader,
            pool = pool,
            libDir = libDir,
          )
        }
      }
    }
  }
  return result
}

// should be the only plugin in lib
@Internal
fun isProductWithTheOnlyDescriptor(platformPrefix: String): Boolean {
  return platformPrefix == PlatformUtils.IDEA_PREFIX ||
         platformPrefix == PlatformUtils.WEB_PREFIX ||
         platformPrefix == PlatformUtils.DBE_PREFIX ||
         platformPrefix == PlatformUtils.GATEWAY_PREFIX
}

private fun getResourceReader(path: String, classLoader: ClassLoader): XMLStreamReader2? {
  if (classLoader is UrlClassLoader) {
    return createNonCoalescingXmlStreamReader(classLoader.getResourceAsBytes(path, false) ?: return null, path)
  }
  else {
    return createNonCoalescingXmlStreamReader(classLoader.getResourceAsStream(path) ?: return null, path)
  }
}

private fun loadCoreProductPlugin(
  path: String,
  context: DescriptorListLoadingContext,
  pathResolver: ClassPathXmlPathResolver,
  useCoreClassLoader: Boolean,
  reader: XMLStreamReader2,
): IdeaPluginDescriptorImpl {
  val dataLoader = object : DataLoader {
    override val emptyDescriptorIfCannotResolve: Boolean
      get() = true

    override fun load(path: String, pluginDescriptorSourceOnly: Boolean) = throw IllegalStateException("must be not called")

    override fun toString() = "product classpath"
  }

  val raw = readModuleDescriptor(reader = reader, readContext = context, pathResolver = pathResolver, dataLoader = dataLoader, includeBase = null, readInto = null)
  val libDir = Paths.get(PathManager.getLibPath())
  val descriptor = IdeaPluginDescriptorImpl(raw = raw, path = libDir, isBundled = true, id = null, moduleName = null, useCoreClassLoader = useCoreClassLoader)
  context.debugData?.recordDescriptorPath(descriptor = descriptor, rawPluginDescriptor = raw, path = path)

  loadModuleDescriptors(descriptor = descriptor, pathResolver = pathResolver, libDir = libDir, context = context, dataLoader = dataLoader)
  descriptor.initByRawDescriptor(raw = raw, context = context, pathResolver = pathResolver, dataLoader = dataLoader)
  return descriptor
}

private fun loadModuleDescriptors(
  descriptor: IdeaPluginDescriptorImpl,
  pathResolver: ClassPathXmlPathResolver,
  libDir: Path,
  context: DescriptorListLoadingContext,
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

    if (moduleDirExists && !pathResolver.isRunningFromSources && moduleName.startsWith("intellij.")) {
      if (loadProductModule(
          loadingStrategy = loadingStrategy,
          moduleDir = moduleDir,
          module = module,
          subDescriptorFile = subDescriptorFile,
          context = context,
          pathResolver = pathResolver,
          dataLoader = dataLoader,
          containerDescriptor = descriptor,
      )) {
        continue
      }
    }

    module.descriptor = descriptor.createSub(
      raw = pathResolver.resolveModuleFile(
        readContext = context,
        dataLoader = dataLoader,
        path = subDescriptorFile,
        readInto = null,
      ),
      descriptorPath = subDescriptorFile,
      context = context,
      moduleName = moduleName,
    )
  }
}

private fun loadProductModule(
  loadingStrategy: ProductLoadingStrategy,
  moduleDir: Path,
  module: PluginContentDescriptor.ModuleItem,
  subDescriptorFile: String,
  context: DescriptorListLoadingContext,
  pathResolver: ClassPathXmlPathResolver,
  dataLoader: DataLoader,
  containerDescriptor: IdeaPluginDescriptorImpl,
): Boolean {
  val moduleName = module.name
  val jarFile = loadingStrategy.findProductContentModuleClassesRoot(moduleName, moduleDir)
  val moduleRaw: RawPluginDescriptor
  if (jarFile == null) {
    // do not log - the severity of the error is determined by the loadingStrategy, the default strategy does not return null at all
    moduleRaw = RawPluginDescriptor().apply { `package` = "unresolved.$moduleName" }
  }
  else {
    moduleRaw = readModuleDescriptor(
      reader = createXmlStreamReader(requireNotNull(module.descriptorContent) {
        "Product module ${module.name} descriptor content is not embedded - corrupted distribution " +
        "(jarFile=$jarFile, containerDescriptor=$containerDescriptor, siblings=${containerDescriptor.content.modules.joinToString()})"
      }.reader()),
      readContext = context,
      pathResolver = pathResolver,
      dataLoader = dataLoader,
      includeBase = null,
      readInto = null,
    )
  }

  val subDescriptor = containerDescriptor.createSub(raw = moduleRaw, descriptorPath = subDescriptorFile, context = context, moduleName = moduleName)
  subDescriptor.jarFiles = jarFile?.let { Java11Shim.INSTANCE.listOf(it) } ?: Java11Shim.INSTANCE.listOf()
  module.descriptor = subDescriptor
  return true
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
  val context = DescriptorListLoadingContext(
    isMissingSubDescriptorIgnored = true,
    productBuildNumber = { buildNumber ?: PluginManagerCore.buildNumber },
    transient = true,
  )

  val fileName = file.fileName.toString()
  if (fileName.endsWith(".jar", ignoreCase = true)) {
    val descriptor = runBlocking {
    loadDescriptorFromJar(file = file, parentContext = context, pluginDir = null, pool = NonShareableJavaZipFilePool())
    }
    if (descriptor != null) {
      return descriptor
    }
  }

  if (!fileName.endsWith(".zip", ignoreCase = true)) {
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
        return runBlocking {
          loadFromPluginDir(file = rootDir, parentContext = context, isUnitTestMode = isUnitTestMode, pool = NonShareableJavaZipFilePool())
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

@RequiresBlockingContext
fun loadDescriptor(
  file: Path,
  isBundled: Boolean,
  pathResolver: PathResolver,
): IdeaPluginDescriptorImpl? {
  DescriptorListLoadingContext().use { context ->
    return runBlocking {
      loadDescriptorFromFileOrDir(file = file, context = context, pathResolver = pathResolver, isBundled = isBundled, pool = NonShareableJavaZipFilePool())
    }
  }
}

@Throws(ExecutionException::class, InterruptedException::class, IOException::class)
fun loadDescriptorsFromOtherIde(
  customPluginDir: Path,
  bundledPluginDir: Path?,
  brokenPluginVersions: Map<PluginId, Set<String?>>?,
  productBuildNumber: BuildNumber?,
): PluginLoadingResult {
  return DescriptorListLoadingContext(
    customDisabledPlugins = Collections.emptySet(),
    customBrokenPluginVersions = brokenPluginVersions,
    productBuildNumber = { productBuildNumber ?: PluginManagerCore.buildNumber },
    isMissingIncludeIgnored = true,
    isMissingSubDescriptorIgnored = true,
  ).use { context ->
    val result = PluginLoadingResult()
    result.addAll(
      descriptors = toSequence(runBlocking {
        loadDescriptorsFromDirs(
          context = context,
          customPluginDir = customPluginDir,
          bundledPluginDir = bundledPluginDir,
          zipFilePool = ZipFilePool.POOL ?: NonShareableJavaZipFilePool(),
          mainClassLoader = DescriptorListLoadingContext::class.java.classLoader,
          loadingStrategy = ProductLoadingStrategy.createPathBasedLoadingStrategy(),
        )
      }, isMainProcess()),
      overrideUseIfCompatible = false,
      productBuildNumber = context.productBuildNumber(),
    )
    result
  }
}

@TestOnly
fun testLoadDescriptorsFromClassPath(loader: ClassLoader): List<IdeaPluginDescriptor> {
  val urlToFilename = collectPluginFilesInClassPath(loader)
  val buildNumber = BuildNumber.fromString("2042.42")!!
  val context = DescriptorListLoadingContext(
    customDisabledPlugins = Collections.emptySet(),
    customBrokenPluginVersions = Collections.emptyMap(),
    productBuildNumber = { buildNumber },
  )
  val result = PluginLoadingResult(checkModuleDependencies = false)
  result.addAll(descriptors = toSequence(runBlocking {
    val pool = ZipFilePool.POOL?.takeIf { !context.transient } ?: NonShareableJavaZipFilePool()
    urlToFilename.map { (url, filename) ->
      async(Dispatchers.IO) {
        loadDescriptorFromResource(
          resource = url,
          filename = filename,
          context = context,
          pathResolver = ClassPathXmlPathResolver(loader, isRunningFromSources = false),
          useCoreClassLoader = true,
          pool = pool,
          libDir = null,
        )
      }
    }
  }, isMainProcess = isMainProcess()), overrideUseIfCompatible = false, productBuildNumber = buildNumber)
  return result.enabledPlugins
}

// do not use it
fun loadCustomDescriptorsFromDirForImportSettings(
  scope: CoroutineScope,
  dir: Path,
  context: DescriptorListLoadingContext,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  return scope.loadDescriptorsFromDir(dir = dir, context = context, isBundled = false, pool = NonShareableJavaZipFilePool())
}

internal fun CoroutineScope.loadDescriptorsFromDir(
  dir: Path,
  context: DescriptorListLoadingContext,
  isBundled: Boolean,
  pool: ZipFilePool,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  if (!Files.isDirectory(dir)) {
    return Collections.emptyList()
  }

  return Files.newDirectoryStream(dir).use { dirStream ->
    dirStream.map { file ->
      async(Dispatchers.IO) {
        loadDescriptorFromFileOrDir(file = file, context = context, isBundled = isBundled, pool = pool)
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
  pool: ZipFilePool,
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
            override val emptyDescriptorIfCannotResolve: Boolean
              get() = true
          }
        }
        else {
          // support for unpacked plugins in classpath, e.g. .../community/build/dependencies/build/kotlin/Kotlin/lib/kotlin-plugin.jar
          basePath = file.parent?.takeIf { !it.endsWith("lib") }?.parent ?: file
          dataLoader = loader
        }

        input = dataLoader.load("META-INF/$filename", pluginDescriptorSourceOnly = true) ?: return null
      }
      else -> return null
    }

    val raw = readModuleDescriptor(
      input = input,
      readContext = context,
      pathResolver = pathResolver,
      dataLoader = dataLoader,
      includeBase = null,
      readInto = null,
      locationSource = file.toString(),
    )
    // it is very important to not set `useCoreClassLoader = true` blindly
    // - product modules must use their own class loader if not running from sources
    val descriptor = IdeaPluginDescriptorImpl(raw = raw, path = basePath, isBundled = true, id = null, moduleName = null, useCoreClassLoader = useCoreClassLoader)
    context.debugData?.recordDescriptorPath(descriptor = descriptor, rawPluginDescriptor = raw, path = filename)

    if (libDir == null) {
      for (module in descriptor.content.modules) {
        val subDescriptorFile = module.configFile ?: "${module.name}.xml"
        val subRaw = pathResolver.resolveModuleFile(readContext = context, dataLoader = dataLoader, path = subDescriptorFile, readInto = null)
        val subDescriptor = descriptor.createSub(raw = subRaw, descriptorPath = subDescriptorFile, context = context, moduleName = module.name)
        if ((pathResolver.isRunningFromSources || isUnitTestMode) && subDescriptor.packagePrefix == null) {
          // no package in run from sources - load module from main classpath
          subDescriptor.jarFiles = Collections.emptyList()
        }
        module.descriptor = subDescriptor
      }
    }
    else {
      loadModuleDescriptors(descriptor = descriptor, pathResolver = pathResolver, libDir = libDir, context = context, dataLoader = dataLoader)
    }
    descriptor.initByRawDescriptor(raw = raw, context = context, pathResolver = pathResolver, dataLoader = dataLoader)
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