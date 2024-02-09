// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.plugins

import com.intellij.openapi.application.PathManager
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
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
  }

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

  abstract fun isOptionalProductModule(moduleName: String): Boolean

  /**
   * Returns `true` if the loader should search for META-INF/plugin.xml files in the core application classpath and load them.
   */
  abstract val shouldLoadDescriptorsFromCoreClassPath: Boolean
}

private class PathBasedProductLoadingStrategy : ProductLoadingStrategy() {
  override fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader) {
  }

  override fun loadBundledPluginDescriptors(
    scope: CoroutineScope,
    bundledPluginDir: Path?,
    isUnitTestMode: Boolean,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
  ): List<Deferred<IdeaPluginDescriptorImpl?>> {
    if (bundledPluginDir != null) {
      val classPathFile = bundledPluginDir.resolve("plugin-classpath.txt")
      if (Files.exists(classPathFile)) {
        return loadFromPluginClasspathDescriptor(
          bundledPluginDir = bundledPluginDir,
          classPathFile = classPathFile,
          scope = scope,
          context = context,
          zipFilePool = zipFilePool,
        )
      }
    }

    val effectiveBundledPluginDir = bundledPluginDir
                                    ?: (if (isUnitTestMode) null else Paths.get(PathManager.getPreInstalledPluginsPath()))
                                    ?: return Collections.emptyList()
    return scope.loadDescriptorsFromDir(dir = effectiveBundledPluginDir, context = context, isBundled = true, pool = zipFilePool)
  }

  private fun loadFromPluginClasspathDescriptor(
    classPathFile: Path?,
    scope: CoroutineScope,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
    bundledPluginDir: Path,
  ): List<Deferred<IdeaPluginDescriptorImpl?>> {
    return Files.readAllLines(classPathFile).mapNotNull { line ->
      if (line.isEmpty()) {
        return@mapNotNull null
      }

      val fileIterator = line.splitToSequence(';').iterator()
      val pluginDir = bundledPluginDir.resolve(fileIterator.next())
      val files = ArrayList<Path>()
      for (p in fileIterator) {
        files.add(pluginDir.resolve(p))
      }
      scope.asyncOrNull(files) {
        val file = files.first()
        val dataLoader = MixedDirAndJarDataLoader(files = files, pool = zipFilePool)
        val pluginPathResolver = PluginXmlPathResolver(files)
        val raw = readModuleDescriptor(
          input = if (file.toString().endsWith(".jar")) {
            dataLoader.load(PluginManagerCore.PLUGIN_XML_PATH)!!
          }
          else {
            Files.newInputStream(file.resolve(PluginManagerCore.PLUGIN_XML_PATH))
          },
          readContext = context,
          pathResolver = pluginPathResolver,
          dataLoader = dataLoader,
          includeBase = null,
          readInto = null,
          locationSource = file.toString(),
        )

        val descriptor = IdeaPluginDescriptorImpl(
          raw = raw,
          path = pluginDir,
          isBundled = true,
          id = null,
          moduleName = null,
        )
        context.debugData?.recordDescriptorPath(descriptor, raw, PluginManagerCore.PLUGIN_XML_PATH)
        descriptor.readExternal(raw = raw, pathResolver = pluginPathResolver, context = context, isSub = false, dataLoader = dataLoader)
        descriptor.jarFiles = files
        descriptor
      }
    }
  }

  override fun isOptionalProductModule(moduleName: String): Boolean = false

  override val shouldLoadDescriptorsFromCoreClassPath: Boolean
    get() = true
}

private fun CoroutineScope.asyncOrNull(files: List<Path>, task: () -> IdeaPluginDescriptorImpl): Deferred<IdeaPluginDescriptorImpl?> {
  return async {
    try {
      task()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      PluginManagerCore.logger.warn("Cannot load plugin descriptor, files:\n  ${files.joinToString(separator = "\n  ")}", e)
      null
    }
  }
}

private class MixedDirAndJarDataLoader(private val files: List<Path>, override val pool: ZipFilePool) : DataLoader {
  // load must return result for sub
  override fun isExcludedFromSubSearch(jarFile: Path): Boolean = true

  override val emptyDescriptorIfCannotResolve: Boolean
    get() = true

  override fun load(path: String): InputStream? {
    val effectivePath = if (path[0] == '/') path.substring(1) else path
    for (file in files) {
      if (file.fileName.toString().endsWith(".jar")) {
        pool.load(file).loadZipEntry(effectivePath)?.let {
          return it
        }
      }
      else {
        try {
          return Files.newInputStream(file.resolve(effectivePath))
        }
        catch (ignore: NoSuchFileException) {
        }
      }
    }

    return null
  }

  override fun toString(): String = "plugin-classpath.txt based data loader"
}