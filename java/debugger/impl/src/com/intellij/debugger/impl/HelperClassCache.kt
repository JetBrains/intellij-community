// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.withDebugContext
import com.intellij.util.lang.JavaVersion
import com.sun.jdi.ClassLoaderReference
import com.sun.jdi.ClassType
import com.sun.jdi.InvocationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.URLClassLoader
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Helper classes are loaded into the user's process to be able to make efficient debugger computations.
 *
 * There several options where a helper class can be defined:
 * 1. The helper class is already available in the current class loader.
 * This can be due to idea-rt.jar usage, or the class was loaded previously to the parent class loader.
 *
 * 2. Otherwise, the helper class is defined in the top parent class loader so that it should be accessible from all child class loaders.
 * However, custom class loaders can ignore this hierarchy, so the class will not be visible from the current class loader.
 *
 * 3. Finally, the helper class can be loaded to a new class loader, as a child to the current class loader.
 * This option is the most reliable but also consumes memory for a new class loader.
 * After the creation of a new class loader, it is cached as a companion for the current class loader
 * and will be used for further loading requests.
 */
internal class HelperClassCache(debugProcess: DebugProcessImpl, managerThread: DebuggerManagerThreadImpl) {
  private val companionClassLoaders = HashMap<ClassLoaderReference?, ClassLoaderReference>()
  private val failedToLoad = HashMap<ClassLoaderReference?, HashSet<String>>()

  init {
    debugProcess.addDebugProcessListener(object : DebugProcessListener {
      override fun processDetached(process: DebugProcess, closedByUser: Boolean) {
        releaseAllClassLoaders()
        debugProcess.removeDebugProcessListener(this)
      }
    })
    launchCleaningTask(managerThread)
  }

  fun getHelperClass(
    evaluationContext: EvaluationContextImpl, forceNewClassLoader: Boolean,
    cls: Class<*>, vararg additionalClassesToLoad: String,
  ): ClassType? {
    val currentClassLoader = evaluationContext.classLoader
    val failed = failedToLoad[currentClassLoader]
    if (failed != null && cls.name in failed) return null
    try {
      val hasCompanionClassLoader = companionClassLoaders[currentClassLoader] != null
      return if (hasCompanionClassLoader || forceNewClassLoader) {
        tryLoadingInCompanion(evaluationContext, cls, *additionalClassesToLoad)
      }
      else {
        tryLoadingInParentOrCompanion(evaluationContext, cls, *additionalClassesToLoad)
      }
    }
    catch (e: ClassDefineTrialException) {
      val exception = e.trials.last()
      for (trial in e.trials.dropLast(1)) {
        exception.addSuppressed(trial)
      }
      throw exception
    }
  }

  private fun tryLoadingInParentOrCompanion(
    evaluationContext: EvaluationContextImpl,
    cls: Class<*>, vararg additionalClassesToLoad: String,
  ): ClassType? {
    // could be already loaded
    val type = tryLoadingInCurrent(evaluationContext, cls.name, *additionalClassesToLoad)
    if (type != null) return type

    val currentClassLoader = evaluationContext.classLoader
    var previousException: ClassDefineTrialException? = null
    try {
      if (currentClassLoader != null) {
        // try loading in parent if not bootstrap
        val parentClassLoader = getTopClassloader(evaluationContext, currentClassLoader)
        val type = tryToDefineInClassLoader(evaluationContext, parentClassLoader, currentClassLoader, cls, *additionalClassesToLoad)
        if (type != null) return type
      }
    }
    catch (e: ClassDefineTrialException) {
      previousException = e
    }
    try {
      // finally, load in companion class loader
      return tryLoadingInCompanion(evaluationContext, cls, *additionalClassesToLoad)
    }
    catch (e: ClassDefineTrialException) {
      val trials = listOfNotNull(previousException?.trials, e.trials).flatten()
      throw ClassDefineTrialException(trials)
    }
  }

  private fun tryLoadingInCurrent(
    evaluationContext: EvaluationContextImpl,
    className: String, vararg additionalClassesToLoad: String,
  ): ClassType? {
    val currentClassLoader = evaluationContext.classLoader
    val loadedTypes = listOf(className, *additionalClassesToLoad).map { tryLoadInClassLoader(evaluationContext, it, currentClassLoader) }
    if (loadedTypes.any { it == null }) return null // ensure all classes can be loaded
    return loadedTypes.first()
  }

  private fun tryLoadingInCompanion(
    evaluationContext: EvaluationContextImpl,
    cls: Class<*>, vararg additionalClassesToLoad: String,
  ): ClassType? {
    try {
      val companionClassLoader = getOrCreateCompanionClassLoader(evaluationContext)
      return tryToDefineInClassLoader(evaluationContext, companionClassLoader, companionClassLoader,
                                      cls, *additionalClassesToLoad)
    }
    catch (e: Throwable) {
      if (e is EvaluateException || e is ClassDefineTrialException) {
        val failed = failedToLoad.getOrPut(evaluationContext.classLoader) { HashSet() }
        failed.add(cls.name)
      }
      throw e
    }
  }

  private fun getOrCreateCompanionClassLoader(evaluationContext: EvaluationContextImpl): ClassLoaderReference {
    val currentClassLoader = evaluationContext.classLoader
    val existing = companionClassLoaders[currentClassLoader]
    if (existing != null) return existing
    val companionClassLoader = ClassLoadingUtils.getClassLoader(evaluationContext, evaluationContext.debugProcess)
    // Check the cache again, as class loader creation causes evaluation and DMT fork, so a race can appear.
    val previouslyCached = companionClassLoaders.putIfAbsent(currentClassLoader, companionClassLoader)
    if (previouslyCached != null) return previouslyCached
    DebuggerUtilsAsync.disableCollection(companionClassLoader)
    return companionClassLoader
  }

  /**
   * As the collection of the newly created class loaders is explicitly disabled,
   * we have to track the disposal of the user's class loaders, and clear unused companion class loaders.
   */
  private fun launchCleaningTask(managerThread: DebuggerManagerThreadImpl) {
    managerThread.coroutineScope.launch {
      while (true) {
        delay(5.seconds)
        withDebugContext(managerThread, PrioritizedTask.Priority.LOWEST) {
          val allClassLoadersSnapshot = HashSet<ClassLoaderReference>()
          allClassLoadersSnapshot.addAll(companionClassLoaders.keys.filterNotNull())
          allClassLoadersSnapshot.addAll(failedToLoad.keys.filterNotNull())
          val collectedClassLoaders = allClassLoadersSnapshot.filter { it.isCollectedAsync() }
          for (classLoader in collectedClassLoaders) {
            val companionClassLoader = companionClassLoaders.remove(classLoader)
            failedToLoad.remove(classLoader)
            if (companionClassLoader != null) {
              DebuggerUtilsImpl.enableCollection(companionClassLoader)
            }
          }
        }
      }
    }
  }

  private fun releaseAllClassLoaders() {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    for (companionClassLoader in companionClassLoaders.values) {
      DebuggerUtilsImpl.enableCollection(companionClassLoader)
    }
    companionClassLoaders.clear()
    failedToLoad.clear()
  }
}

private fun tryLoadInClassLoader(evaluationContext: EvaluationContextImpl, className: String, classLoader: ClassLoaderReference?): ClassType? {
  try {
    return evaluationContext.debugProcess.findClass(evaluationContext, className, classLoader) as ClassType?
  }
  catch (e: EvaluateException) {
    val cause = e.cause
    if (cause is InvocationException && "java.lang.ClassNotFoundException" == cause.exception().type().name()) {
      return null
    }
    throw e
  }
}

private fun tryToDefineInClassLoader(
  evaluationContext: EvaluationContextImpl,
  loaderForDefine: ClassLoaderReference, loaderForLookup: ClassLoaderReference,
  cls: Class<*>, vararg additionalClassesToLoad: String,
): ClassType? {
  for (fqn in listOf(cls.name, *additionalClassesToLoad)) {
    val alreadyDefined = tryLoadInClassLoader(evaluationContext, fqn, loaderForDefine)
    if (alreadyDefined != null) continue // ensure no double define
    try {
      // we use `cls.classLoader` as a fallback loader because additional classes are usually available from the same class loader
      defineClass(fqn, evaluationContext, loaderForDefine, cls.classLoader)
    }
    catch (e: ClassDefineTrialException) {
      // Another debugger worker may define the class already in case many thread breakpoint pauses.
      val definedInParallel = tryLoadInClassLoader(evaluationContext, fqn, loaderForDefine)
      if (definedInParallel != null) continue
      throw e
    }
  }

  return tryLoadInClassLoader(evaluationContext, cls.name, loaderForLookup)
}

/**
 * Determines the top-level class loader in a hierarchy, starting from the given `currentClassLoader`.
 *
 * It is used to define the helper class to avoid defining it in every classloader for performance reasons
 */
private fun getTopClassloader(
  evaluationContext: EvaluationContextImpl,
  currentClassLoader: ClassLoaderReference,
): ClassLoaderReference {
  val process = evaluationContext.debugProcess
  val classLoaderClass = process.findClass(evaluationContext, "java.lang.ClassLoader", currentClassLoader)
  val parentMethod = DebuggerUtils.findMethod(classLoaderClass, "getParent", "()Ljava/lang/ClassLoader;")
  checkNotNull(parentMethod) { "getParent method is not available" }

  var classLoader = currentClassLoader

  while (true) {
    val parent = process.invokeInstanceMethod(evaluationContext, classLoader, parentMethod, emptyList(), 0, true)
    if (parent !is ClassLoaderReference) {
      return classLoader
    }
    classLoader = parent
  }
}

private val ideaRtPath by lazy { Path(DebuggerUtilsImpl.getIdeaRtPath()).toUri().toURL() }

/**
 * Tries to load class [name] from idea-rt.jar, falling back to [sourceClassLoader] if not found there
 */
private fun defineClass(
  name: String,
  evaluationContext: EvaluationContextImpl,
  classLoader: ClassLoaderReference?,
  sourceClassLoader: ClassLoader?,
) {
  try {
    val rtJarClassLoader = URLClassLoader(arrayOf(ideaRtPath), null)
    val classFilePath = "${name.replace('.', '/')}.class"

    val stream =
      rtJarClassLoader.getResourceAsStream(classFilePath)
      ?: sourceClassLoader?.getResourceAsStream(classFilePath)
      ?: throw EvaluateException("Unable to find $name class bytes in idea-rt.jar: $ideaRtPath")
    val bytes = stream.use { it.readAllBytes() }
    val classJavaVersion = extractJavaVersion(bytes) ?: throw EvaluateException("Cannot define $name, as class file version cannot be read")
    val targetJavaVersion = evaluationContext.virtualMachineProxy.javaVersion()
    if (classJavaVersion.feature > targetJavaVersion.feature) {
      throw EvaluateException("Unable to define $name class compiled for Java $classJavaVersion: target VM is Java $targetJavaVersion")
    }
    try {
      ClassLoadingUtils.defineClass(name, bytes, evaluationContext, classLoader)
    }
    catch (e: EvaluateException) {
      throw ClassDefineTrialException(listOf(e))
    }
  }
  catch (ioe: IOException) {
    throw EvaluateException("Unable to read $name class bytes", ioe)
  }
}

private fun extractJavaVersion(bytes: ByteArray): JavaVersion? {
  if (bytes.size < 8) return null

  val major = DataInputStream(ByteArrayInputStream(bytes, 6, 2)).use { it.readUnsignedShort() }
  if (major < 44) return null
  return JavaVersion.compose(major - 44) // 44 = 1.0, 45 = 1.1, 46 = 1.2 etc.
}

private class ClassDefineTrialException(val trials: List<EvaluateException>) : Exception()
