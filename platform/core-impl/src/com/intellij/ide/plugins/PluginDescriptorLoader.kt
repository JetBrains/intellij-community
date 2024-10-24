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
import com.intellij.platform.util.putMoreLikelyPluginJarsFirst
import com.intellij.util.Java11Shim
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
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
import java.io.*
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.jar.JarInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLStreamException
import kotlin.io.path.extension
import kotlin.io.path.inputStream

private val LOG: Logger
  get() = PluginManagerCore.logger

@TestOnly
fun loadDescriptor(file: Path, parentContext: DescriptorListLoadingContext, pool: ZipFilePool): IdeaPluginDescriptorImpl? =
  loadDescriptorFromFileOrDir(file, parentContext, pool)

internal fun loadForCoreEnv(pluginRoot: Path, fileName: String): IdeaPluginDescriptorImpl? {
  val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val parentContext = DescriptorListLoadingContext()
  val relativePath = "${PluginManagerCore.META_INF}${fileName}"
  if (Files.isDirectory(pluginRoot)) {
    return loadDescriptorFromDir(pluginRoot, parentContext, NonShareableJavaZipFilePool(), pathResolver, relativePath, isBundled = true, isEssential = true)
  }
  else {
    @Suppress("SSBasedInspection")
    return runBlocking {
      loadDescriptorFromJar(pluginRoot, parentContext, NonShareableJavaZipFilePool(), pathResolver, relativePath, isBundled = true, isEssential = true)
    }
  }
}

fun loadDescriptorFromDir(
  dir: Path,
  context: DescriptorListLoadingContext,
  pool: ZipFilePool,
  pathResolver: PathResolver,
  descriptorRelativePath: String = PluginManagerCore.PLUGIN_XML_PATH,
  isBundled: Boolean,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  pluginDir: Path? = null,
): IdeaPluginDescriptorImpl? {
  try {
    val dataLoader = LocalFsDataLoader(dir)
    val input = Files.newInputStream(dir.resolve(descriptorRelativePath))
    val descriptor = loadDescriptorFromStream(input, context, pathResolver, dataLoader, dir, pluginDir, isBundled, useCoreClassLoader, descriptorRelativePath, pool)
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
  context: DescriptorListLoadingContext,
  pool: ZipFilePool,
  pathResolver: PathResolver,
  descriptorRelativePath: String = PluginManagerCore.PLUGIN_XML_PATH,
  isBundled: Boolean = false,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  pluginDir: Path? = null,
): IdeaPluginDescriptorImpl? {
  var closeable: Closeable? = null
  try {
    val resolver = pool.load(file)
    closeable = resolver as? Closeable
    val dataLoader = ImmutableZipFileDataLoader(resolver, zipPath = file)
    val input = dataLoader.load(descriptorRelativePath, pluginDescriptorSourceOnly = true) ?: return null
    val descriptor = loadDescriptorFromStream(input, context, pathResolver, dataLoader, file, pluginDir, isBundled, useCoreClassLoader, descriptorRelativePath, pool)
    descriptor.jarFiles = Collections.singletonList(descriptor.pluginPath)
    return descriptor
  }
  catch (e: Throwable) {
    if (isEssential) {
      throw if (e is XMLStreamException) RuntimeException("Cannot read $file", e) else e
    }
    context.reportCannotLoad(file, e)
  }
  finally {
    closeable?.close()
  }
  return null
}

private fun loadDescriptorFromStream(
  input: InputStream,
  context: DescriptorListLoadingContext,
  pathResolver: PathResolver,
  dataLoader: DataLoader,
  fileOrDir: Path,
  pluginDir: Path?,
  isBundled: Boolean,
  useCoreClassLoader: Boolean,
  descriptorRelativePath: String,
  pool: ZipFilePool,
): IdeaPluginDescriptorImpl {
  val raw = readModuleDescriptor(input, context, pathResolver, dataLoader, includeBase = null, readInto = null, locationSource = fileOrDir.toString())
  val descriptor = IdeaPluginDescriptorImpl(raw, pluginDir ?: fileOrDir, isBundled, id = null, moduleName = null, useCoreClassLoader = useCoreClassLoader)
  context.debugData?.recordDescriptorPath(descriptor, raw, descriptorRelativePath)
  initMainDescriptorByRaw(descriptor, raw, pathResolver, context, dataLoader, pluginDir ?: fileOrDir, pool)
  return descriptor
}

@VisibleForTesting
fun initMainDescriptorByRaw(
  descriptor: IdeaPluginDescriptorImpl,
  raw: RawPluginDescriptor,
  pathResolver: PathResolver,
  context: DescriptorListLoadingContext,
  dataLoader: DataLoader,
  pluginDir: Path,
  pool: ZipFilePool,
) {
  val moduleDir = pluginDir.resolve("lib/modules").takeIf { Files.isDirectory(it) }
  for (module in descriptor.content.modules) {
    val subDescriptorFile = module.configFile ?: "${module.name}.xml"
    if (module.descriptorContent == null) {
      val jarFile = moduleDir?.resolve("${module.name}.jar")
      if (jarFile != null && Files.exists(jarFile)) {
        val subRaw = loadModuleFromSeparateJar(pool, jarFile, subDescriptorFile, context, dataLoader)
        val subDescriptor = descriptor.createSub(subRaw, subDescriptorFile, context, module)
        subDescriptor.jarFiles = Collections.singletonList(jarFile)
        module.descriptor = subDescriptor
      }
      else {
        val subRaw = pathResolver.resolveModuleFile(context, dataLoader, subDescriptorFile, readInto = null)
        module.descriptor = descriptor.createSub(subRaw, subDescriptorFile, context, module)
      }
    }
    else {
      val subRaw = readModuleDescriptor(createXmlStreamReader(module.descriptorContent), context, dataLoader)
      val subDescriptor = descriptor.createSub(subRaw, subDescriptorFile, context, module)
      if (subRaw.`package` == null || subRaw.isSeparateJar) {
        subDescriptor.jarFiles = Collections.singletonList(pluginDir.resolve("lib/modules/${module.name}.jar"))
      }
      module.descriptor = subDescriptor
    }
  }

  descriptor.initByRawDescriptor(raw, context, pathResolver, dataLoader)
}

@VisibleForTesting
fun loadDescriptorFromFileOrDirInTests(file: Path, context: DescriptorListLoadingContext, isBundled: Boolean): IdeaPluginDescriptorImpl? =
  loadDescriptorFromFileOrDir(file, context, NonShareableJavaZipFilePool(), isBundled = isBundled, isEssential = true, isUnitTestMode = true)

@Internal
fun loadDescriptorFromFileOrDir(
  file: Path,
  context: DescriptorListLoadingContext,
  pool: ZipFilePool,
  pathResolver: PathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
  isBundled: Boolean = false,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  isUnitTestMode: Boolean = false,
): IdeaPluginDescriptorImpl? = when {
  Files.isDirectory(file) -> {
    loadFromPluginDir(file, context, pool, pathResolver, isBundled, isEssential, useCoreClassLoader, isUnitTestMode)
  }
  file.extension.equals("jar", ignoreCase = true) -> {
    loadDescriptorFromJar(file, context, pool, pathResolver, isBundled = isBundled, isEssential = isEssential, useCoreClassLoader = useCoreClassLoader)
  }
  else -> null
}

// [META-INF] [classes] lib/*.jar
private fun loadFromPluginDir(
  file: Path,
  parentContext: DescriptorListLoadingContext,
  pool: ZipFilePool,
  pathResolver: PathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
  isBundled: Boolean = false,
  isEssential: Boolean = false,
  useCoreClassLoader: Boolean = false,
  isUnitTestMode: Boolean = false,
): IdeaPluginDescriptorImpl? {
  val pluginJarFiles = resolveArchives(file)
  if (!pluginJarFiles.isNullOrEmpty()) {
    if (pluginJarFiles.size > 1) {
      putMoreLikelyPluginJarsFirst(pluginDirName = file.fileName.toString(), filesInLibUnderPluginDir = pluginJarFiles)
    }
    val pluginPathResolver = PluginXmlPathResolver(pluginJarFiles = pluginJarFiles, pool = pool)
    for (jarFile in pluginJarFiles) {
      loadDescriptorFromJar(jarFile, parentContext, pool, pluginPathResolver, isBundled = isBundled, isEssential = isEssential, useCoreClassLoader = useCoreClassLoader, pluginDir = file)?.let {
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
        loadDescriptorFromDir(it, parentContext, pool, pathResolver, isBundled = isBundled, isEssential = isEssential, useCoreClassLoader = useCoreClassLoader, pluginDir = file)
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

private fun resolveArchives(path: Path): MutableList<Path>? = try {
  Files.newDirectoryStream(path.resolve("lib")).use { stream ->
    stream.filterTo(ArrayList()) {
      val ext = it.extension
      ext.equals("jar", ignoreCase = true) || ext.equals("zip", ignoreCase = true)
    }
  }
}
catch (_: NoSuchFileException) {
  null
}

private fun CoroutineScope.loadDescriptorsFromProperty(context: DescriptorListLoadingContext, pool: ZipFilePool): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val pathProperty = System.getProperty("plugin.path") ?: return Collections.emptyList()

  // gradle-intellij-plugin heavily depends on this property to have core class loader plugins during tests
  val useCoreClassLoaderForPluginsFromProperty = java.lang.Boolean.getBoolean("idea.use.core.classloader.for.plugin.path")
  val t = StringTokenizer(pathProperty, File.pathSeparatorChar + ",")
  val list = mutableListOf<Deferred<IdeaPluginDescriptorImpl?>>()
  while (t.hasMoreTokens()) {
    val file = Paths.get(t.nextToken())
    list.add(async(Dispatchers.IO) {
      loadDescriptorFromFileOrDir(file, context, pool, useCoreClassLoader = useCoreClassLoaderForPluginsFromProperty)
    })
  }
  return list
}

suspend fun loadDescriptors(zipFilePoolDeferred: Deferred<ZipFilePool>, mainClassLoaderDeferred: Deferred<ClassLoader>?): Pair<DescriptorListLoadingContext, PluginLoadingResult> {
  val isUnitTestMode = PluginManagerCore.isUnitTestMode
  val isRunningFromSources = PluginManagerCore.isRunningFromSources()
  val result = DescriptorListLoadingContext(
    isMissingSubDescriptorIgnored = true,
    isMissingIncludeIgnored = isUnitTestMode,
    checkOptionalConfigFileUniqueness = isUnitTestMode || isRunningFromSources,
  ).use { context ->
    context to loadDescriptors(context, isUnitTestMode, isRunningFromSources, zipFilePoolDeferred, mainClassLoaderDeferred)
  }
  return result
}

@Suppress("DeferredIsResult")
internal fun CoroutineScope.scheduleLoading(
  zipFilePoolDeferred: Deferred<ZipFilePool>,
  mainClassLoaderDeferred: Deferred<ClassLoader>?,
  logDeferred: Deferred<Logger>?,
): Deferred<PluginSet> {
  val resultDeferred = async(CoroutineName("plugin descriptor loading")) {
    loadDescriptors(zipFilePoolDeferred, mainClassLoaderDeferred)
  }
  val pluginSetDeferred = async {
    val (context, loadingResult) = resultDeferred.await()
    PluginManagerCore.initializeAndSetPlugins(context, loadingResult)
  }
  // logging is not as a part of plugin set job for performance reasons
  launch {
    val (context, loadingResult) = resultDeferred.await()
    logPlugins(pluginSetDeferred.await().allPlugins, context, loadingResult, logSupplier = {
      // make sure that logger is ready to use (not a console logger)
      logDeferred?.await()
      LOG
    })
  }
  return pluginSetDeferred
}

private suspend fun logPlugins(
  plugins: Collection<IdeaPluginDescriptorImpl>,
  context: DescriptorListLoadingContext,
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
    listDeferred = ProductLoadingStrategy.strategy.loadPluginDescriptors(
      scope = this,
      context,
      customPluginDir = Paths.get(PathManager.getPluginsPath()),
      bundledPluginDir = null,
      isUnitTestMode,
      isRunningFromSources,
      zipFilePool,
      mainClassLoader,
    )
    extraListDeferred = loadDescriptorsFromProperty(context, zipFilePool)
  }

  val buildNumber = context.productBuildNumber()
  val loadingResult = PluginLoadingResult()

  val isMainProcess = isMainProcess()
  loadingResult.addAll(descriptors = toSequence(listDeferred, isMainProcess), overrideUseIfCompatible = false, buildNumber)
  // plugins added via property shouldn't be overridden to avoid plugin root detection issues when running external plugin tests
  loadingResult.addAll(descriptors = toSequence(extraListDeferred, isMainProcess), overrideUseIfCompatible = true, buildNumber)

  if (isUnitTestMode && loadingResult.enabledPluginsById.size <= 1) {
    // we're running in unit test mode, but the classpath doesn't contain any plugins; try to load bundled plugins anyway
    loadingResult.addAll(
      descriptors = toSequence(coroutineScope {
        val dir = Paths.get(PathManager.getPreInstalledPluginsPath())
        loadDescriptorsFromDir(dir, context, isBundled = true, pool = zipFilePoolDeferred.await())
      }, isMainProcess),
      overrideUseIfCompatible = false,
      buildNumber,
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

private fun isMainProcess(): Boolean? =
  if (java.lang.Boolean.getBoolean("ide.per.project.instance")) !PathManager.getPluginsDir().fileName.toString().startsWith("perProject_")
  else null

internal fun CoroutineScope.loadPluginDescriptorsImpl(
  context: DescriptorListLoadingContext,
  isUnitTestMode: Boolean,
  isRunningFromSources: Boolean,
  mainClassLoader: ClassLoader,
  zipFilePool: ZipFilePool,
  customPluginDir: Path,
  bundledPluginDir: Path?,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val platformPrefix = PlatformUtils.getPlatformPrefix()

  val result = ArrayList<Deferred<IdeaPluginDescriptorImpl?>>()
  if (isUnitTestMode) {
    result.addAll(loadCoreModules(
      context,
      platformPrefix,
      isUnitTestMode = true,
      isInDevServerMode = false,
      isRunningFromSources = true,
      classLoader = mainClassLoader,
      pool = zipFilePool,
      result = result,
    ))
    result.addAll(loadDescriptorsFromDir(customPluginDir, context, isBundled = false, zipFilePool))
    bundledPluginDir?.let {
      result.addAll(loadDescriptorsFromDir(dir = it, context, isBundled = true, zipFilePool))
    }
    return result
  }

  val effectiveBundledPluginDir = bundledPluginDir ?: Paths.get(PathManager.getPreInstalledPluginsPath())
  val data = try {
    // use only if the format is supported (first byte it is a version)
    Files.readAllBytes(effectiveBundledPluginDir.resolve("plugin-classpath.txt")).takeIf { it[0] == 2.toByte() }
  }
  catch (_: NoSuchFileException) {
    null
  }

  if (data == null) {
    result.addAll(loadCoreModules(context, platformPrefix, isUnitTestMode = false, AppMode.isDevServer(), isRunningFromSources, zipFilePool, mainClassLoader, result))
    result.addAll(loadDescriptorsFromDir(customPluginDir, context, isBundled = false, zipFilePool))
    result.addAll(loadDescriptorsFromDir(effectiveBundledPluginDir, context, isBundled = true, zipFilePool))
  }
  else {
    val byteInput = ByteArrayInputStream(data, 2, data.size)
    val input = DataInputStream(byteInput)
    val descriptorSize = input.readInt()
    val descriptorStart = data.size - byteInput.available()
    input.skipBytes(descriptorSize)
    // Gateway will be removed soon
    result.add(async {
      loadCoreProductPlugin(
        PluginManagerCore.PLUGIN_XML_PATH,
        context,
        ClassPathXmlPathResolver(classLoader = mainClassLoader, isRunningFromSources = false),
        useCoreClassLoader = platformPrefix.startsWith("CodeServer") || forceUseCoreClassloader(),
        reader = if (PlatformUtils.isGateway()) {
          getResourceReader(PluginManagerCore.PLUGIN_XML_PATH, classLoader = mainClassLoader)!!
        }
        else {
          createXmlStreamReader(data, descriptorStart, descriptorSize)
        },
      )
    })

    result.addAll(loadDescriptorsFromDir(dir = customPluginDir, context = context, isBundled = false, pool = zipFilePool))

    loadFromPluginClasspathDescriptor(input, jarOnly = data[1] == 1.toByte(), context, zipFilePool, effectiveBundledPluginDir, scope = this, result)
  }
  return result
}

private fun loadFromPluginClasspathDescriptor(
  input: DataInputStream,
  jarOnly: Boolean,
  context: DescriptorListLoadingContext,
  zipFilePool: ZipFilePool,
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
        loadPluginDescriptor(fileItems, zipFilePool, jarOnly, pluginDescriptorData, context, pluginDir)
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

private fun loadPluginDescriptor(
  fileItems: Array<FileItem>,
  zipFilePool: ZipFilePool,
  jarOnly: Boolean,
  pluginDescriptorData: ByteArray,
  context: DescriptorListLoadingContext,
  pluginDir: Path,
): IdeaPluginDescriptorImpl {
  val item = fileItems.first()
  val dataLoader = MixedDirAndJarDataLoader(fileItems, zipFilePool, jarOnly)
  val pluginPathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val descriptorInput = createNonCoalescingXmlStreamReader(input = pluginDescriptorData, locationSource = item.path)
  val raw = readModuleDescriptor(descriptorInput, context, dataLoader, pluginPathResolver)

  val descriptor = IdeaPluginDescriptorImpl(raw, pluginDir, isBundled = true, id = null, moduleName = null)
  context.debugData?.recordDescriptorPath(descriptor, raw, PluginManagerCore.PLUGIN_XML_PATH)
  for (module in descriptor.content.modules) {
    var classPath: List<Path>? = null
    val subDescriptorFile = module.configFile ?: "${module.name}.xml"
    val subRaw = if (module.descriptorContent == null) {
      val input = dataLoader.load(subDescriptorFile, pluginDescriptorSourceOnly = true)
      if (input == null) {
        val jarFile = pluginDir.resolve("lib/modules/${module.name}.jar")
        classPath = Collections.singletonList(jarFile)
        loadModuleFromSeparateJar(zipFilePool, jarFile, subDescriptorFile, context, dataLoader)
      }
      else {
        readModuleDescriptor(createXmlStreamReader(input), context, dataLoader, pluginPathResolver)
      }
    }
    else {
      val subRaw = readModuleDescriptor(createXmlStreamReader(module.descriptorContent), context, dataLoader)
      if (subRaw.`package` == null || subRaw.isSeparateJar) {
        classPath = Collections.singletonList(pluginDir.resolve("lib/modules/${module.name}.jar"))
      }
      subRaw
    }

    val subDescriptor = descriptor.createSub(subRaw, subDescriptorFile, context, module)
    if (classPath != null) {
      subDescriptor.jarFiles = classPath
    }
    module.descriptor = subDescriptor
  }

  descriptor.initByRawDescriptor(raw, context, pluginPathResolver, dataLoader)
  descriptor.jarFiles = fileItems.map { it.file }
  return descriptor
}

private class MixedDirAndJarDataLoader(
  private val files: Array<FileItem>,
  private val pool: ZipFilePool,
  private val jarOnly: Boolean,
) : DataLoader {
  // loading must return a result for the sub
  override fun isExcludedFromSubSearch(jarFile: Path): Boolean = true

  override val emptyDescriptorIfCannotResolve: Boolean = true

  override fun load(path: String, pluginDescriptorSourceOnly: Boolean): InputStream? {
    val effectivePath = if (path[0] == '/') path.substring(1) else path
    for (item in files) {
      if (jarOnly || item.path.endsWith(".jar")) {
        var resolver = item.resolver
        if (resolver == null) {
          resolver = pool.load(item.file)
          if (resolver !is Closeable) {
            item.resolver = resolver
          }
        }

        val result = resolver.loadZipEntry(effectivePath)
        if (resolver is Closeable) {
          resolver.close()
        }
        if (result != null) {
          return result
        }
      }
      else {
        try {
          return Files.newInputStream(item.file.resolve(effectivePath))
        }
        catch (_: NoSuchFileException) { }
      }

      if (jarOnly && pluginDescriptorSourceOnly) {
        break
      }
    }

    return null
  }

  override fun toString(): String = "plugin-classpath.txt based data loader"
}

private fun loadModuleFromSeparateJar(
  pool: ZipFilePool,
  jarFile: Path,
  subDescriptorFile: String,
  context: DescriptorListLoadingContext,
  dataLoader: DataLoader,
): RawPluginDescriptor {
  val resolver = pool.load(jarFile)
  try {
    val input = resolver.loadZipEntry(subDescriptorFile) ?: throw IllegalStateException("Module descriptor $subDescriptorFile not found in $jarFile")
    // product module is always fully resolved and do not contain `xi:include`
    return readModuleDescriptor(createXmlStreamReader(input, jarFile.toString()), context, dataLoader)
  }
  finally {
    (resolver as? Closeable)?.close()
  }
}

private data class FileItem(@JvmField val file: Path, @JvmField val path: String) {
  @JvmField
  @Volatile
  var resolver: ZipFilePool.EntryResolver? = null
}

private fun CoroutineScope.loadCoreModules(
  context: DescriptorListLoadingContext,
  platformPrefix: String,
  isUnitTestMode: Boolean,
  isInDevServerMode: Boolean,
  isRunningFromSources: Boolean,
  pool: ZipFilePool,
  classLoader: ClassLoader,
  result: MutableList<Deferred<IdeaPluginDescriptorImpl?>>,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  val pathResolver = ClassPathXmlPathResolver(classLoader = classLoader, isRunningFromSources = isRunningFromSources && !isInDevServerMode)
  val useCoreClassLoader = pathResolver.isRunningFromSources || platformPrefix.startsWith("CodeServer") || forceUseCoreClassloader()
  if (loadCorePlugin(platformPrefix, isInDevServerMode, isUnitTestMode, isRunningFromSources, context, pathResolver, useCoreClassLoader, classLoader, result)) {
    return result
  }
  @Suppress("UrlHashCode")
  val urlToFilename = collectPluginFilesInClassPath(classLoader)
  if (urlToFilename.isNotEmpty()) {
    val libDir = if (useCoreClassLoader) null else Paths.get(PathManager.getLibPath())
    urlToFilename.mapTo(result) { (url, filename) ->
      async(Dispatchers.IO) {
        loadDescriptorFromResource(url, filename, context, pathResolver, useCoreClassLoader, pool, libDir)
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
  context: DescriptorListLoadingContext,
  pathResolver: ClassPathXmlPathResolver,
  useCoreClassLoader: Boolean,
  classLoader: ClassLoader,
  result: MutableList<Deferred<IdeaPluginDescriptorImpl?>>,
): Boolean {
  if (isProductWithTheOnlyDescriptor(platformPrefix) && (isInDevServerMode || (!isUnitTestMode && !isRunningFromSources))) {
    result.add(async(Dispatchers.IO) {
      val reader = getResourceReader(PluginManagerCore.PLUGIN_XML_PATH, classLoader)!!
      loadCoreProductPlugin(PluginManagerCore.PLUGIN_XML_PATH, context, pathResolver, useCoreClassLoader, reader)
    })
    return true
  }

  result.add(async(Dispatchers.IO) {
    val path = "${PluginManagerCore.META_INF}${platformPrefix}Plugin.xml"
    val reader = getResourceReader(path, classLoader) ?: return@async null
    loadCoreProductPlugin(path, context, pathResolver, useCoreClassLoader, reader)
  })
  return false
}

// should be the only plugin in lib
@Internal
fun isProductWithTheOnlyDescriptor(platformPrefix: String): Boolean =
  platformPrefix == PlatformUtils.IDEA_PREFIX ||
  platformPrefix == PlatformUtils.WEB_PREFIX ||
  platformPrefix == PlatformUtils.DBE_PREFIX ||
  platformPrefix == PlatformUtils.GATEWAY_PREFIX

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
    override val emptyDescriptorIfCannotResolve: Boolean = true
    override fun load(path: String, pluginDescriptorSourceOnly: Boolean) = throw IllegalStateException("must be not called")
    override fun toString() = "product classpath"
  }

  val raw = readModuleDescriptor(reader, context, dataLoader, pathResolver)
  val libDir = Paths.get(PathManager.getLibPath())
  val descriptor = IdeaPluginDescriptorImpl(raw, libDir, isBundled = true, id = null, moduleName = null, useCoreClassLoader = useCoreClassLoader)
  context.debugData?.recordDescriptorPath(descriptor, raw, path)
  loadModuleDescriptors(descriptor, pathResolver, libDir, context, dataLoader)
  descriptor.initByRawDescriptor(raw, context, pathResolver, dataLoader)
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

    if (
      moduleDirExists &&
      !pathResolver.isRunningFromSources && moduleName.startsWith("intellij.") &&
      loadProductModule(loadingStrategy, moduleDir, module, subDescriptorFile, context, pathResolver, dataLoader, descriptor)
    ) continue

    val raw = pathResolver.resolveModuleFile(context, dataLoader, subDescriptorFile, readInto = null)
    module.descriptor = descriptor.createSub(raw, subDescriptorFile, context, module)
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
  val moduleRaw = if (jarFile == null) {
    // do not log - the severity of the error is determined by the loadingStrategy, the default strategy does not return null at all
    RawPluginDescriptor().apply { `package` = "unresolved.$moduleName" }
  }
  else {
    val reader = createXmlStreamReader(requireNotNull(module.descriptorContent) {
      "Product module ${module.name} descriptor content is not embedded - corrupted distribution " +
      "(jarFile=$jarFile, containerDescriptor=$containerDescriptor, siblings=${containerDescriptor.content.modules.joinToString()})"
    })
    readModuleDescriptor(reader, context, dataLoader, pathResolver, includeBase = null, readInto = null)
  }
  val subDescriptor = containerDescriptor.createSub(moduleRaw, subDescriptorFile, context, module)
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
fun loadDescriptorFromArtifact(file: Path, buildNumber: BuildNumber?): IdeaPluginDescriptorImpl? {
  val context = DescriptorListLoadingContext(
    isMissingSubDescriptorIgnored = true,
    productBuildNumber = { buildNumber ?: PluginManagerCore.buildNumber },
    transient = true,
  )

  val ext = file.extension
  if (ext.equals("jar", ignoreCase = true)) {
    @Suppress("SSBasedInspection")
    val descriptor = runBlocking {
      loadDescriptorFromJar(file, context, NonShareableJavaZipFilePool(), PluginXmlPathResolver.DEFAULT_PATH_RESOLVER)
    }
    if (descriptor != null) {
      return descriptor
    }
  }

  if (!ext.equals("zip", ignoreCase = true)) {
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
          loadFromPluginDir(rootDir, context, NonShareableJavaZipFilePool(), isUnitTestMode = PluginManagerCore.isUnitTestMode)
        }
      }
    }
    catch (_: NoSuchFileException) { }
  }
  finally {
    NioFiles.deleteRecursively(outputDir)
  }

  return null
}

fun loadDescriptor(file: Path, isBundled: Boolean, pathResolver: PathResolver): IdeaPluginDescriptorImpl? {
  DescriptorListLoadingContext().use { context ->
    @Suppress("SSBasedInspection")
    return runBlocking {
      loadDescriptorFromFileOrDir(file, context, NonShareableJavaZipFilePool(), pathResolver, isBundled)
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
    @Suppress("SSBasedInspection")
    result.addAll(
      descriptors = toSequence(runBlocking {
        val classLoader = DescriptorListLoadingContext::class.java.classLoader
        val pool = ZipFilePool.POOL ?: NonShareableJavaZipFilePool()
        loadPluginDescriptorsImpl(context, PluginManagerCore.isUnitTestMode, PluginManagerCore.isRunningFromSources(), classLoader, pool, customPluginDir, bundledPluginDir)
      }, isMainProcess()),
      overrideUseIfCompatible = false,
      context.productBuildNumber(),
    )
    result
  }
}

suspend fun loadDescriptorsFromCustomPluginDir(customPluginDir: Path, ignoreCompatibility: Boolean = false) : PluginLoadingResult{
  return DescriptorListLoadingContext(isMissingIncludeIgnored = true, isMissingSubDescriptorIgnored = true).use { context ->
    val result = PluginLoadingResult()
    result.addAll(
      descriptors = toSequence(coroutineScope {
        loadDescriptorsFromDir(customPluginDir, context, ignoreCompatibility, ZipFilePool.POOL ?: NonShareableJavaZipFilePool())
      }, isMainProcess()),
      overrideUseIfCompatible = false,
      context.productBuildNumber(),
    )
    result
  }
}

@TestOnly
fun testLoadDescriptorsFromClassPath(loader: ClassLoader): List<IdeaPluginDescriptor> {
  @Suppress("UrlHashCode")
  val urlToFilename = collectPluginFilesInClassPath(loader)
  val buildNumber = BuildNumber.fromString("2042.42")!!
  val context = DescriptorListLoadingContext(
    customDisabledPlugins = Collections.emptySet(),
    customBrokenPluginVersions = Collections.emptyMap(),
    productBuildNumber = { buildNumber },
  )
  val result = PluginLoadingResult(checkModuleDependencies = false)
  @Suppress("SSBasedInspection")
  result.addAll(toSequence(runBlocking {
    val pool = ZipFilePool.POOL?.takeIf { !context.transient } ?: NonShareableJavaZipFilePool()
    urlToFilename.map { (url, filename) ->
      async(Dispatchers.IO) {
        loadDescriptorFromResource(url, filename, context, ClassPathXmlPathResolver(loader, isRunningFromSources = false), useCoreClassLoader = true, pool, libDir = null)
      }
    }
  }, isMainProcess = isMainProcess()), overrideUseIfCompatible = false, buildNumber)
  return result.enabledPlugins
}

// do not use it
@Internal
fun loadCustomDescriptorsFromDirForImportSettings(scope: CoroutineScope, dir: Path, context: DescriptorListLoadingContext): List<Deferred<IdeaPluginDescriptorImpl?>> =
  scope.loadDescriptorsFromDir(dir = dir, context = context, isBundled = false, pool = NonShareableJavaZipFilePool())

internal fun CoroutineScope.loadDescriptorsFromDir(
  dir: Path,
  context: DescriptorListLoadingContext,
  isBundled: Boolean,
  pool: ZipFilePool,
): List<Deferred<IdeaPluginDescriptorImpl?>> {
  if (!Files.isDirectory(dir)) return Collections.emptyList() else return Files.newDirectoryStream(dir).use { dirStream ->
    dirStream.map { file ->
      async(Dispatchers.IO) {
        loadDescriptorFromFileOrDir(file, context, pool, isBundled = isBundled)
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
            override val emptyDescriptorIfCannotResolve = true
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

    val raw = readModuleDescriptor(input, context, pathResolver, dataLoader, includeBase = null, readInto = null, locationSource = file.toString())
    // it is very important to not set `useCoreClassLoader = true` blindly
    // - product modules must use their own class loader if not running from sources
    val descriptor = IdeaPluginDescriptorImpl(raw, basePath, isBundled = true, id = null, moduleName = null, useCoreClassLoader = useCoreClassLoader)
    context.debugData?.recordDescriptorPath(descriptor, raw, filename)

    if (libDir == null) {
      val runFromSources = pathResolver.isRunningFromSources || PluginManagerCore.isUnitTestMode || forceUseCoreClassloader()
      for (module in descriptor.content.modules) {
        val subDescriptorFile = module.configFile ?: "${module.name}.xml"
        val subRaw = pathResolver.resolveModuleFile(context, dataLoader, subDescriptorFile, readInto = null)
        val subDescriptor = descriptor.createSub(subRaw, subDescriptorFile, context, module)
        if (runFromSources && subDescriptor.packagePrefix == null) {
          // no package in run from sources - load module from main classpath
          subDescriptor.jarFiles = Collections.emptyList()
        }
        module.descriptor = subDescriptor
      }
    }
    else {
      loadModuleDescriptors(descriptor, pathResolver, libDir, context, dataLoader)
    }
    descriptor.initByRawDescriptor(raw, context, pathResolver, dataLoader)
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
  val ext = file.extension
  if (ext.equals("jar", ignoreCase = true)) {
    file.inputStream().buffered().use { stream ->
      return readDescriptorFromJarStream(stream, file)
    }
  }
  else if (ext.equals("zip", ignoreCase = true)) {
    ZipInputStream(file.inputStream().buffered()).use { stream ->
      val pattern = Regex("[^/]+/lib/[^/]+\\.jar")
      while (true) {
        val entry = stream.nextEntry ?: break
        if (entry.name.matches(pattern)) {
          val descriptor = readDescriptorFromJarStream(stream, file)
          if (descriptor != null) return descriptor
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
          return IdeaPluginDescriptorImpl(raw, path, isBundled = false, id = null, moduleName = null)
        }
      }
      catch (e: XMLStreamException) {
        throw IOException(e)
      }
    }
  }
  return null
}
