// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.plugins.cl

import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.util.Java11Shim
import com.intellij.util.lang.ClassPath
import com.intellij.util.lang.ClasspathCache
import com.intellij.util.lang.Resource
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.io.InputStream
import java.io.Writer
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Implements filtering for a plugin class loader.
 * This is necessary to distinguish classes from different modules when they are packed to a single JAR file.
 */
@ApiStatus.Internal
interface ResolveScopeManager {
  /**
   * Returns
   * * `null` if the class loader should try loading the class from its own classpath first;
   * * `""` if the class loader should skip searching for `name` in its own classpath and try loading it from the parent classloaders;
   * * non-empty string describing an error if the class must not be requested from this class loader; an error will be thrown.
   */
  fun isDefinitelyAlienClass(name: String, packagePrefix: String, force: Boolean): String?
}

private val defaultResolveScopeManager = object : ResolveScopeManager {
  override fun isDefinitelyAlienClass(name: String, packagePrefix: String, force: Boolean): String? = null
}

private val EMPTY_CLASS_LOADER_ARRAY = arrayOfNulls<ClassLoader>(0)
private val KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES = computeKotlinStdlibClassesUsedInSignatures()

private var logStream: Writer? = null
private val parentListCacheIdCounter = AtomicInteger()

@ApiStatus.Internal
class PluginClassLoader(
  classPath: ClassPath,
  private val parents: Array<IdeaPluginDescriptorImpl>,
  private val pluginDescriptor: PluginDescriptor,
  private val coreLoader: ClassLoader,
  resolveScopeManager: ResolveScopeManager?,
  packagePrefix: String?,
  private val libDirectories: List<Path>,
) : UrlClassLoader(classPath), PluginAwareClassLoader {
  // cache of a computed list of all parents (not only direct)
  @Volatile
  private var allParents: Array<ClassLoader>? = null

  @Volatile
  private var allParentsLastCacheId = 0

  // to simplify analyzing of heap dump (dynamic plugin reloading)
  private val pluginId: PluginId = pluginDescriptor.pluginId
  private val packagePrefix: String? = if (packagePrefix == null || packagePrefix.endsWith('.')) packagePrefix else "$packagePrefix."
  private val edtTime = AtomicLong()
  private val backgroundTime = AtomicLong()
  private val loadedClassCounter = AtomicInteger()
  @Suppress("SSBasedInspection")
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + CoroutineName(pluginId.idString))
  private val _resolveScopeManager = resolveScopeManager ?: defaultResolveScopeManager

  companion object {
    init {
      val isParallelCapable = registerAsParallelCapable()
      assert(isParallelCapable)
    }

    init {
      var logStreamCandidate: Writer? = null
      var debugFilePath = System.getProperty("plugin.classloader.debug", "")
      if (!debugFilePath.isEmpty()) {
        try {
          if (debugFilePath.startsWith("~/") || debugFilePath.startsWith("~\\")) {
            debugFilePath = System.getProperty("user.home") + debugFilePath.substring(1)
          }
          logStreamCandidate = Files.newBufferedWriter(Paths.get(debugFilePath))
          ShutDownTracker.getInstance().registerShutdownTask {
            try {
              logStream?.close()
            }
            catch (e: IOException) {
              logger<PluginClassLoader>().error(e)
            }
          }
        }
        catch (e: IOException) {
          logger<PluginClassLoader>().error(e)
        }
      }
      logStream = logStreamCandidate
    }
  }

  fun getLibDirectories(): List<Path> = libDirectories

  override fun getPackagePrefix(): String? = packagePrefix

  override fun getPluginCoroutineScope(): CoroutineScope = scope

  @ApiStatus.Internal
  override fun getState(): Int {
    val job = scope.coroutineContext.job
    return if (job.isActive) PluginAwareClassLoader.ACTIVE else PluginAwareClassLoader.UNLOAD_IN_PROGRESS
  }

  @ApiStatus.Internal
  fun setState(state: Int) {
    if (state == PluginAwareClassLoader.UNLOAD_IN_PROGRESS) {
      // job.isActive returns `false` after this call
      scope.coroutineContext.job.cancel(null)
      return
    }

    throw IllegalStateException("Unexpected state: $state")
  }

  override fun getEdtTime(): Long = edtTime.get()

  override fun getBackgroundTime(): Long = backgroundTime.get()

  override fun getLoadedClassCount(): Long = loadedClassCounter.get().toLong()

  public override fun loadClass(name: String, resolve: Boolean): Class<*> {
    tryLoadingClass(name = name, forceLoadFromSubPluginClassloader = false)?.let {
      return it
    }

    flushDebugLog()
    throw ClassNotFoundException("$name $this")
  }

  /**
   * See [https://stackoverflow.com/a/5428795](https://stackoverflow.com/a/5428795) about resolve flag.
   */
  override fun tryLoadingClass(name: String, forceLoadFromSubPluginClassloader: Boolean): Class<*>? {
    if (mustBeLoadedByPlatform(name)) {
      return coreLoader.loadClass(name)
    }

    val fileNameWithoutExtension = name.replace('.', '/')
    val fileName = fileNameWithoutExtension + ClasspathCache.CLASS_EXTENSION
    val packageNameHash = ClasspathCache.getPackageNameHash(fileNameWithoutExtension, fileNameWithoutExtension.lastIndexOf('/'))
    val startTime = if (StartUpMeasurer.measuringPluginStartupCosts) System.nanoTime() else -1
    var c: Class<*>?
    var error: PluginException? = null
    try {
      val consistencyError = packagePrefix?.let {
        _resolveScopeManager.isDefinitelyAlienClass(name = name, packagePrefix = it, force = forceLoadFromSubPluginClassloader)
      }
      if (consistencyError == null) {
        c = loadClassInsideSelf(name = name, fileName = fileName, packageNameHash = packageNameHash, forceLoadFromSubPluginClassloader = forceLoadFromSubPluginClassloader)
      }
      else {
        if (!consistencyError.isEmpty()) {
          error = PluginException(consistencyError, pluginId)
        }
        c = null
      }
    }
    catch (e: IOException) {
      throw ClassNotFoundException(name, e)
    }

    if (c == null) {
      for (classloader in getAllParents()) {
        if (classloader is PluginClassLoader) {
          try {
            val consistencyError = classloader.packagePrefix?.let {
              classloader._resolveScopeManager.isDefinitelyAlienClass(name = name, packagePrefix = it, force = forceLoadFromSubPluginClassloader)
            }
            if (consistencyError != null) {
              if (!consistencyError.isEmpty() && error == null) {
                // yes, we blame requestor plugin
                error = PluginException(consistencyError, pluginId)
              }
              continue
            }
            c = classloader.loadClassInsideSelf(name = name, fileName = fileName, packageNameHash = packageNameHash, forceLoadFromSubPluginClassloader = false)
          }
          catch (e: IOException) {
            throw ClassNotFoundException(name, e)
          }
          if (c != null) {
            break
          }
        }
        else if (classloader is UrlClassLoader) {
          try {
            val resolveScopeManager = classloader.resolveScopeManager
            val consistencyError = resolveScopeManager?.apply(name, forceLoadFromSubPluginClassloader)
            if (consistencyError != null) {
              if (!consistencyError.isEmpty() && error == null) {
                // yes, we blame requestor plugin
                error = PluginException(consistencyError, pluginId)
              }
              continue
            }
            c = classloader.loadClassWithPrecomputedMeta(name, fileName, fileNameWithoutExtension, packageNameHash)
          }
          catch (e: IOException) {
            throw ClassNotFoundException(name, e)
          }
          if (c != null) {
            break
          }
        }
        else {
          try {
            c = classloader.loadClass(name)
            if (c != null) {
              break
            }
          }
          catch (_: ClassNotFoundException) {
            // ignore and continue
          }
        }
      }
      if (error != null) {
        throw error
      }
    }

    if (startTime != -1L) {
      // EventQueue.isDispatchThread() is expensive
      (if (EDT.isCurrentThreadEdt()) edtTime else backgroundTime).addAndGet(System.nanoTime() - startTime)
    }
    return c
  }

  private fun getAllParents(): Array<ClassLoader> {
    var result = allParents
    if (result != null && allParentsLastCacheId == parentListCacheIdCounter.get()) {
      return result
    }

    if (parents.isEmpty()) {
      result = arrayOf(coreLoader)
      allParents = result
      return result
    }

    val parentSet = LinkedHashSet<ClassLoader>()
    val queue = ArrayDeque<ClassLoader>()
    collectClassLoaders(queue)
    while (true) {
      val classLoader = queue.pollFirst() ?: break
      if (!parentSet.add(classLoader)) {
        continue
      }
      if (classLoader is PluginClassLoader) {
        classLoader.collectClassLoaders(queue)
      }
    }
    parentSet.add(coreLoader)
    result = parentSet.toArray(EMPTY_CLASS_LOADER_ARRAY)
    allParents = result
    allParentsLastCacheId = parentListCacheIdCounter.get()
    return result
  }

  private fun collectClassLoaders(queue: Deque<ClassLoader>) {
    for (parent in parents) {
      val classLoader = parent.pluginClassLoader
      if (classLoader != null && classLoader !== coreLoader) {
        queue.add(classLoader)
      }
    }
  }

  fun clearParentListCache() {
    allParents = null
  }

  override fun hasLoadedClass(name: String): Boolean {
    val consistencyError = packagePrefix?.let {
      _resolveScopeManager.isDefinitelyAlienClass(name = name, packagePrefix = it, force = false)
    }
    return consistencyError == null && super.hasLoadedClass(name)
  }

  override fun loadClassInsideSelf(name: String): Class<*>? {
    val fileNameWithoutExtension = name.replace('.', '/')
    val fileName = fileNameWithoutExtension + ClasspathCache.CLASS_EXTENSION
    val packageNameHash = ClasspathCache.getPackageNameHash(fileNameWithoutExtension, fileNameWithoutExtension.lastIndexOf('/'))
    return loadClassInsideSelf(name, fileName, packageNameHash, false)
  }

  private fun loadClassInsideSelf(name: String, fileName: String, packageNameHash: Long, forceLoadFromSubPluginClassloader: Boolean): Class<*>? {
    synchronized(getClassLoadingLock(name)) {
      var c = findLoadedClass(name)
      if (c?.classLoader === this) {
        return c
      }

      c = try {
        classPath.findClass(name, fileName, packageNameHash, classDataConsumer)
      }
      catch (e: LinkageError) {
        logStream?.let { logClass(name = name, logStream = it, exception = e) }
        flushDebugLog()
        throw PluginException("Cannot load class $name (\n  error: ${e.message},\n  classLoader=$this\n)", e, pluginId)
      }
      if (c == null) {
        return null
      }

      loadedClassCounter.incrementAndGet()
      logStream?.let { logClass(name = name, logStream = it, exception = null) }
      return c
    }
  }

  private fun logClass(name: String, logStream: Writer, exception: LinkageError?) {
    try {
      // must be as one write call since write is performed from multiple threads
      val descriptorPath = (pluginDescriptor as IdeaPluginDescriptor).descriptorPath
      val specifier = if (descriptorPath == null) "m" else "sub = $descriptorPath"
      logStream.write("""$name [$specifier] ${pluginId.idString}${if (packagePrefix == null) "" else ":$packagePrefix"}
${if (exception == null) "" else exception.message}""")
    }
    catch (_: IOException) {
    }
  }

  override fun findResource(name: String): URL? {
    return doFindResource(name = name, f1 = { it.url }, f2 = ClassLoader::getResource)
  }

  override fun getResourceAsBytes(name: String, checkParents: Boolean): ByteArray? {
    super.getResourceAsBytes(name, checkParents)?.let {
      return it
    }

    if (!checkParents) {
      return null
    }

    for (classloader in getAllParents()) {
      if (classloader is UrlClassLoader) {
        classloader.classPath.findResource(name)?.let {
          return it.bytes
        }
      }
      else {
        classloader.getResourceAsStream(name)?.use { input ->
          return input.readBytes()
        }
      }
    }
    return null
  }

  override fun getResourceAsStream(name: String): InputStream? {
    return doFindResource(
      name = name,
      f1 = { resource ->
        try {
          resource.inputStream
        }
        catch (e: IOException) {
          logger<PluginClassLoader>().error(e)
          null
        }
      },
      f2 = { cl, path ->
        try {
          cl.getResourceAsStream(path)
        }
        catch (e: Exception) {
          logger<PluginClassLoader>().error(e)
          null
        }
      },
    )
  }

  private inline fun <T : Any> doFindResource(name: String, f1: (Resource) -> T?, f2: (ClassLoader, String) -> T?): T? {
    val canonicalPath = toCanonicalPath(name)
    classPath.findResource(canonicalPath)?.let {
      return f1(it)
    }

    for (classloader in getAllParents()) {
      if (classloader is PluginClassLoader) {
        classloader.classPath.findResource(canonicalPath)?.let {
          return f1(it)
        }
      }
      else {
        f2(classloader, canonicalPath)?.let { return it }
      }
    }

    if (canonicalPath.startsWith('/') && classPath.findResource(canonicalPath.substring(1)) != null) {
      // reporting malformed paths only when there's a resource at the right one - which is rarely the case
      // (see also `UrlClassLoader#doFindResource`)
      val message = "Calling `ClassLoader#getResource` with leading slash doesn't work; strip"
      logger<PluginClassLoader>().error(message, PluginException(name, pluginId))
    }
    return null
  }

  override fun findResources(name: String): Enumeration<URL> {
    val resources = ArrayList<Enumeration<URL>>()
    resources.add(classPath.getResources(name))
    for (classloader in getAllParents()) {
      if (classloader is PluginClassLoader) {
        resources.add(classloader.classPath.getResources(name))
      }
      else {
        try {
          resources.add(classloader.getResources(name))
        }
        catch (_: IOException) {
        }
      }
    }
    return DeepEnumeration(resources)
  }

  override fun findLibrary(libName: String): String? {
    if (!libDirectories.isEmpty()) {
      val libFileName = System.mapLibraryName(libName)
      val iterator = libDirectories.listIterator(libDirectories.size)
      while (iterator.hasPrevious()) {
        val libFile = iterator.previous().resolve(libFileName)
        if (Files.exists(libFile)) {
          return libFile.toString()
        }
      }
    }
    return null
  }

  override fun getPluginId(): PluginId = pluginId

  override fun getModuleId(): String? = (pluginDescriptor as IdeaPluginDescriptorImpl).moduleName

  override fun getPluginDescriptor(): PluginDescriptor = pluginDescriptor

  override fun toString(): String {
    return "${javaClass.simpleName}(" +
           "plugin=$pluginDescriptor, " +
           "packagePrefix=$packagePrefix, " +
           "state=${if (state == PluginAwareClassLoader.ACTIVE) "active" else "unload in progress"}, " +
           "parents=${parents.joinToString()}, " +
           ")"
  }

  @Suppress("FunctionName")
  @TestOnly
  fun _getParents(): List<IdeaPluginDescriptorImpl> = parents.toList()
}

private class DeepEnumeration(private val list: List<Enumeration<URL>>) : Enumeration<URL> {
  private var index = 0

  override fun hasMoreElements(): Boolean {
    while (index < list.size) {
      val e = list.get(index)
      if (e.hasMoreElements()) {
        return true
      }
      index++
    }
    return false
  }

  override fun nextElement(): URL {
    if (!hasMoreElements()) {
      throw NoSuchElementException()
    }
    return list.get(index).nextElement()
  }
}

// only `kotlin.` and not `kotlinx.` classes here (see mustBeLoadedByPlatform - name.startsWith("kotlin."))
private fun computeKotlinStdlibClassesUsedInSignatures(): Set<String> {
  val result = mutableListOf(
    "kotlin.Function",
    "kotlin.sequences.Sequence",
    "kotlin.ranges.IntRange",
    "kotlin.ranges.IntRange\$Companion",
    "kotlin.ranges.IntProgression",
    "kotlin.ranges.ClosedRange",
    "kotlin.ranges.IntProgressionIterator",
    "kotlin.ranges.IntProgression\$Companion",
    "kotlin.ranges.IntProgression",
    "kotlin.collections.IntIterator",
    "kotlin.Lazy", "kotlin.Unit",
    "kotlin.Pair", "kotlin.Triple",
    "kotlin.jvm.internal.DefaultConstructorMarker",
    "kotlin.jvm.internal.ClassBasedDeclarationContainer",
    "kotlin.properties.ReadWriteProperty",
    "kotlin.properties.ReadOnlyProperty",
    "kotlin.coroutines.ContinuationInterceptor",
    "kotlin.coroutines.Continuation",

    "kotlin.coroutines.CoroutineContext",
    "kotlin.coroutines.CoroutineContext\$Element",
    "kotlin.coroutines.CoroutineContext\$Key",
    "kotlin.coroutines.EmptyCoroutineContext",

    "kotlin.Result",
    "kotlin.Result\$Failure",
    "kotlin.Result\$Companion",  // even though it's an internal class, it can leak (and it does) into API surface because it's exposed by public
    // `kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED` property
    "kotlin.coroutines.intrinsics.CoroutineSingletons",
    "kotlin.coroutines.AbstractCoroutineContextElement",
    "kotlin.coroutines.AbstractCoroutineContextKey",
    "kotlin.coroutines.jvm.internal.ContinuationImpl", // IDEA-295189
    "kotlin.coroutines.jvm.internal.BaseContinuationImpl", // IDEA-295189
    "kotlin.coroutines.jvm.internal.CoroutineStackFrame", // IDEA-295189
    "kotlin.time.Duration",
    "kotlin.time.Duration\$Companion",
    "kotlin.jvm.internal.ReflectionFactory",
    "kotlin.jvm.internal.Reflection",
    "kotlin.jvm.internal.Lambda",
  )
  System.getProperty("idea.kotlin.classes.used.in.signatures")?.let {
    result.addAll(it.splitToSequence(',').map(String::trim))
  }
  return Java11Shim.INSTANCE.copyOf(result)
}

private fun mustBeLoadedByPlatform(name: @NonNls String): Boolean {
  if (name.startsWith("java.")) {
    return true
  }

  // Some commonly used classes from kotlin-runtime must be loaded by the platform classloader.
  // Otherwise, if a plugin bundles its own version
  // of kotlin-runtime.jar, it won't be possible to call the platform's methods with these types in a signatures from such a plugin.
  // We assume that these classes don't change between Kotlin versions, so it's safe to always load them from the platform's kotlin-runtime.
  return name.startsWith("kotlin.") &&
         (name.startsWith("kotlin.jvm.functions.") ||
          // Those are kotlin-reflect related classes, but unfortunately, they are placed in kotlin-stdlib.
          // Since we always want to load reflect from platform, we should force those classes with platform classloader as well.
          name.startsWith("kotlin.reflect.") ||
          name.startsWith("kotlin.jvm.internal.CallableReference") ||
          name.startsWith("kotlin.jvm.internal.ClassReference") ||
          name.startsWith("kotlin.jvm.internal.FunInterfaceConstructorReference") ||
          name.startsWith("kotlin.jvm.internal.FunctionReference") ||
          name.startsWith("kotlin.jvm.internal.MutablePropertyReference") ||
          name.startsWith("kotlin.jvm.internal.PropertyReference") ||
          name.startsWith("kotlin.jvm.internal.TypeReference") ||
          name.startsWith("kotlin.jvm.internal.LocalVariableReference") ||
          name.startsWith("kotlin.jvm.internal.MutableLocalVariableReference") ||
          KOTLIN_STDLIB_CLASSES_USED_IN_SIGNATURES.contains(name))
}

private fun flushDebugLog() {
  val logStream = logStream ?: return
  try {
    logStream.flush()
  }
  catch (_: IOException) {
  }
}