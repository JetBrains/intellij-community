// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.plugins

import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * This class is temporarily added to support two ways of loading the plugin descriptors: [the old one][PathBasedProductLoadingStrategy]
 * which is based on layout of JAR files in the IDE installation directory and [the new one][com.intellij.platform.bootstrap.ModuleBasedProductLoadingStrategy]
 * which uses information from runtime module descriptors.
 */
@ApiStatus.Internal
abstract class ProductLoadingStrategy {
  companion object {
    @Volatile
    private var ourStrategy: ProductLoadingStrategy? = null

    var strategy: ProductLoadingStrategy
      get() {
        if (ourStrategy == null) {
          ourStrategy = PathBasedProductLoadingStrategy()
        }
        return ourStrategy!!
      }
      set(value) {
        ourStrategy = value
      }

    /**
     * Creates an instance of the old path-based strategy even if module-based strategy is used in the product.
     * This is needed to load plugin descriptors from another IDE during settings import.
     */
    internal fun createPathBasedLoadingStrategy(): ProductLoadingStrategy = PathBasedProductLoadingStrategy()
  }

  /**
   * Returns ID of current [ProductMode][com.intellij.platform.runtime.product.ProductMode].
   */
  abstract val currentModeId: String

  /**
   * Adds roots of all modules from the main module group and their dependencies to the classpath of [bootstrapClassLoader].
   */
  abstract fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader)

  abstract fun loadBundledPluginDescriptors(
    scope: CoroutineScope,
    bundledPluginDir: Path?,
    isUnitTestMode: Boolean,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
  ): List<Deferred<IdeaPluginDescriptorImpl?>>

  /** Loads descriptors for custom (non-bundled) plugins from [customPluginDir] */
  abstract fun loadCustomPluginDescriptors(
    scope: CoroutineScope,
    customPluginDir: Path,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
  ): Collection<Deferred<IdeaPluginDescriptorImpl?>>
  
  abstract fun isOptionalProductModule(moduleName: String): Boolean

  /**
   * Returns the path to a JAR or directory containing classes from [moduleName] registered as a content module in the product, or `null`
   * if the mentioned content module isn't present in the distribution.
   */
  abstract fun findProductContentModuleClassesRoot(moduleName: String, moduleDir: Path): Path?

  /**
   * Returns `true` if the loader should search for META-INF/plugin.xml files in the core application classpath and load them.
   */
  abstract val shouldLoadDescriptorsFromCoreClassPath: Boolean
}

private class PathBasedProductLoadingStrategy : ProductLoadingStrategy() {
  /* This property returns hardcoded Strings instead of ProductMode, because currently ProductMode class isn't available in dependencies of 
     this module */
  override val currentModeId: String
    get() = if (AppMode.isRemoteDevHost()) "backend" else "local_IDE"

  override fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader) {
  }

  override fun loadBundledPluginDescriptors(
    scope: CoroutineScope,
    bundledPluginDir: Path?,
    isUnitTestMode: Boolean,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
  ): List<Deferred<IdeaPluginDescriptorImpl?>> {
    val effectiveBundledPluginDir = when {
      bundledPluginDir != null -> bundledPluginDir
      isUnitTestMode -> return Collections.emptyList()
      else -> Paths.get(PathManager.getPreInstalledPluginsPath())
    }

    val classPathFile = effectiveBundledPluginDir.resolve("plugin-classpath.txt")
    val data = try {
      Files.readAllBytes(classPathFile)
    }
    catch (ignored: NoSuchFileException) {
      null
    }

    if (data == null || data[0] != 1.toByte()) {
      return scope.loadDescriptorsFromDir(dir = effectiveBundledPluginDir, context = context, isBundled = true, pool = zipFilePool)
    }

    return loadFromPluginClasspathDescriptor(
      data = data,
      context = context,
      zipFilePool = zipFilePool,
      bundledPluginDir = effectiveBundledPluginDir,
      scope = scope,
    ).asList()
  }

  override fun loadCustomPluginDescriptors(
    scope: CoroutineScope,
    customPluginDir: Path,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
  ): Collection<Deferred<IdeaPluginDescriptorImpl?>> {
    return scope.loadDescriptorsFromDir(dir = customPluginDir, context = context, isBundled = false, pool = zipFilePool)
  }

  private fun loadFromPluginClasspathDescriptor(
    data: ByteArray,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
    bundledPluginDir: Path,
    scope: CoroutineScope,
  ): Array<Deferred<IdeaPluginDescriptorImpl?>> {
    val jarOnly = data[1] == 1.toByte()
    val input = DataInputStream(ByteArrayInputStream(data, 2, data.size))
    val pluginCount = input.readUnsignedShort()
    return Array(pluginCount) {
      val fileCount = input.readUnsignedShort()

      val pluginDir = bundledPluginDir.resolve(input.readUTF())
      val descriptorSize = input.readInt()
      val pluginDescriptorData = if (descriptorSize == 0) null else ByteArray(descriptorSize).also { input.read(it) }
      val fileItems = Array(fileCount) {
        val path = input.readUTF()
        var file = pluginDir.resolve(path)
        if (!jarOnly) {
          file = file.normalize()
        }
        FileItem(file = file, path = path)
      }

      scope.async {
        try {
          loadPluginDescriptor(
            fileItems = fileItems,
            zipFilePool = zipFilePool,
            jarOnly = jarOnly,
            pluginDescriptorData = pluginDescriptorData,
            context = context,
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
      }
    }
  }

  private suspend fun loadPluginDescriptor(
    fileItems: Array<FileItem>,
    zipFilePool: ZipFilePool,
    jarOnly: Boolean,
    pluginDescriptorData: ByteArray?,
    context: DescriptorListLoadingContext,
    pluginDir: Path,
  ): IdeaPluginDescriptorImpl {
    val item = fileItems.first()
    val dataLoader = MixedDirAndJarDataLoader(files = fileItems, pool = zipFilePool, jarOnly = jarOnly)
    val pluginPathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
    val raw = withContext(Dispatchers.IO) {
      val descriptorInput = when {
        pluginDescriptorData != null -> createNonCoalescingXmlStreamReader(input = pluginDescriptorData, locationSource = item.path)
        jarOnly || item.path.endsWith(".jar") -> {
          createNonCoalescingXmlStreamReader(
            input = dataLoader.load(PluginManagerCore.PLUGIN_XML_PATH, pluginDescriptorSourceOnly = true)!!,
            locationSource = item.path,
          )
        }
        else -> {
          createNonCoalescingXmlStreamReader(Files.newInputStream(item.file.resolve(PluginManagerCore.PLUGIN_XML_PATH)), item.path)
        }
      }
      readModuleDescriptor(
        reader = descriptorInput,
        readContext = context,
        pathResolver = pluginPathResolver,
        dataLoader = dataLoader,
        includeBase = null,
        readInto = null,
      )
    }

    val descriptor = IdeaPluginDescriptorImpl(
      raw = raw,
      path = pluginDir,
      isBundled = true,
      id = null,
      moduleName = null,
    )
    context.debugData?.recordDescriptorPath(descriptor, raw, PluginManagerCore.PLUGIN_XML_PATH)
    for (module in descriptor.content.modules) {
      val subDescriptorFile = module.configFile ?: "${module.name}.xml"
      val input = dataLoader.load(subDescriptorFile, pluginDescriptorSourceOnly = true)
      var classPath: List<Path>? = null
      val subRaw = if (input == null) {
        val jarFile = pluginDir.resolve("lib/modules/${module.name}.jar")
        if (Files.exists(jarFile)) {
          classPath = Collections.singletonList(jarFile)
          loadModuleFromSeparateJar(pool = zipFilePool, jarFile = jarFile, subDescriptorFile = subDescriptorFile, context = context, dataLoader = dataLoader)
        }
        else {
          throw RuntimeException("Cannot resolve $subDescriptorFile (dataLoader=$dataLoader)")
        }
      }
      else {
        readModuleDescriptor(
          input = input,
          readContext = context,
          pathResolver = pluginPathResolver,
          dataLoader = dataLoader,
          includeBase = null,
          readInto = null,
          locationSource = null,
        )
      }

      val subDescriptor = descriptor.createSub(
        raw = subRaw,
        descriptorPath = subDescriptorFile,
        context = context,
        moduleName = module.name,
      )
      if (classPath != null) {
        subDescriptor.jarFiles = classPath
      }
      module.descriptor = subDescriptor
    }
    descriptor.initByRawDescriptor(raw = raw, context = context, pathResolver = pluginPathResolver, dataLoader = dataLoader)
    descriptor.jarFiles = fileItems.map { it.file }
    return descriptor
  }

  override fun isOptionalProductModule(moduleName: String): Boolean = false

  override fun findProductContentModuleClassesRoot(moduleName: String, moduleDir: Path): Path {
    return moduleDir.resolve("$moduleName.jar")
  }

  override val shouldLoadDescriptorsFromCoreClassPath: Boolean
    get() = true
}

private data class FileItem(
  @JvmField val file: Path,
  @JvmField val path: String,
) {
  @JvmField @Volatile var resolver: ZipFilePool.EntryResolver? = null
}

private class MixedDirAndJarDataLoader(
  private val files: Array<FileItem>,
  private val pool: ZipFilePool,
  private val jarOnly: Boolean,
) : DataLoader {
  // load must return result for sub
  override fun isExcludedFromSubSearch(jarFile: Path): Boolean = true

  override val emptyDescriptorIfCannotResolve: Boolean
    get() = true

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
        catch (ignore: NoSuchFileException) {
        }
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
    val entry = resolver.loadZipEntry(subDescriptorFile) ?: throw IllegalStateException("Module descriptor $subDescriptorFile not found in $jarFile")
    return readModuleDescriptor(
      input = entry,
      readContext = context,
      // product module is always fully resolved and do not contain `xi:include`
      pathResolver = null,
      dataLoader = dataLoader,
      includeBase = null,
      readInto = null,
      locationSource = jarFile.toString(),
    )
  }
  finally {
    (resolver as? Closeable)?.close()
  }
}
