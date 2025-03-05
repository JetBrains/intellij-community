// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.ClassLoaderInfo.DefinedInCompanionClassLoader
import com.intellij.debugger.impl.ClassLoaderInfo.LoadFailedMarker
import com.sun.jdi.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URLClassLoader
import java.util.*
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

private sealed interface ClassLoaderInfo {
  object LoadFailedMarker : ClassLoaderInfo
  class DefinedInCompanionClassLoader(val classLoader: ClassLoaderReference) : ClassLoaderInfo
}

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
  private val evaluationClassLoaderMapping = HashMap<ClassLoaderReference?, ClassLoaderInfo>()

  init {
    debugProcess.addDebugProcessListener(object : DebugProcessAdapterImpl() {
      override fun processDetached(process: DebugProcessImpl?, closedByUser: Boolean) {
        releaseAllClassLoaders()
      }
    })
    launchCleaningTask(managerThread)
  }

  fun getHelperClass(
    evaluationContext: EvaluationContextImpl, forceNewClassLoader: Boolean,
    className: String, vararg additionalClassesToLoad: String,
  ): ClassType? {
    val currentClassLoader = evaluationContext.classLoader
    val classLoaderInfo = evaluationClassLoaderMapping[currentClassLoader]
    if (classLoaderInfo == null && !forceNewClassLoader) {
      return tryLoadingInParentOrCompanion(evaluationContext, className, *additionalClassesToLoad)
    }
    return when (classLoaderInfo) {
      is LoadFailedMarker -> null
      is DefinedInCompanionClassLoader? -> tryLoadingInCompanion(classLoaderInfo, evaluationContext, className, *additionalClassesToLoad)
    }
  }

  private fun tryLoadingInParentOrCompanion(
    evaluationContext: EvaluationContextImpl,
    className: String, vararg additionalClassesToLoad: String,
  ): ClassType? {
    // could be already loaded
    val type = tryLoadingInCurrent(evaluationContext, className, *additionalClassesToLoad)
    if (type != null) return type

    val currentClassLoader = evaluationContext.classLoader
    if (currentClassLoader != null) {
      // try loading in parent if not bootstrap
      val parentClassLoader = getTopClassloader(evaluationContext, currentClassLoader)
      val type = tryToDefineInClassLoader(evaluationContext, parentClassLoader, currentClassLoader, className, *additionalClassesToLoad)
      if (type != null) return type
    }
    // finally, load in companion class loader
    return tryLoadingInCompanion(null, evaluationContext, className, *additionalClassesToLoad)
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
    currentInfo: DefinedInCompanionClassLoader?,
    evaluationContext: EvaluationContextImpl,
    className: String, vararg additionalClassesToLoad: String,
  ): ClassType? {
    val companionClassLoader = currentInfo?.classLoader
                               ?: ClassLoadingUtils.getClassLoader(evaluationContext, evaluationContext.debugProcess)
    val type = tryToDefineInClassLoader(evaluationContext, companionClassLoader, companionClassLoader,
                                        className, *additionalClassesToLoad) ?: return null
    if (currentInfo == null) {
      DebuggerUtilsImpl.disableCollection(companionClassLoader)
      evaluationClassLoaderMapping[evaluationContext.classLoader] = DefinedInCompanionClassLoader(companionClassLoader)
    }
    return type
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
          val allClassLoadersSnapshot = evaluationClassLoaderMapping.keys.filterNotNull()
          val collectedClassLoaders = allClassLoadersSnapshot.filter {
            DebuggerUtilsAsync.isCollected(it).await()
          }
          for (classLoader in collectedClassLoaders) {
            val info = evaluationClassLoaderMapping.remove(classLoader)
            if (info is DefinedInCompanionClassLoader) {
              DebuggerUtilsImpl.enableCollection(info.classLoader)
            }
          }
        }
      }
    }
  }

  private fun releaseAllClassLoaders() {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    val createdLoaders = evaluationClassLoaderMapping.values.filterIsInstance<DefinedInCompanionClassLoader>()
    for (info in createdLoaders) {
      DebuggerUtilsImpl.enableCollection(info.classLoader)
    }
    evaluationClassLoaderMapping.clear()
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
  className: String, vararg additionalClassesToLoad: String,
): ClassType? {
  for (fqn in listOf(className, *additionalClassesToLoad)) {
    val alreadyDefined = tryLoadInClassLoader(evaluationContext, fqn, loaderForDefine)
    if (alreadyDefined != null) continue // ensure no double define
    if (!defineClass(fqn, evaluationContext, loaderForDefine)) return null
  }
  return tryLoadInClassLoader(evaluationContext, className, loaderForLookup)
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
  Objects.requireNonNull<Method?>(parentMethod, "getParent method is not available")

  var classLoader = currentClassLoader

  while (true) {
    val parent = process.invokeInstanceMethod(
      evaluationContext, classLoader, parentMethod!!,
      mutableListOf<Value?>(), 0, true
    )
    if (parent !is ClassLoaderReference) {
      return classLoader
    }
    classLoader = parent
  }
}

private val ideaRtPath by lazy { Path(DebuggerUtilsImpl.getIdeaRtPath()).toUri().toURL() }

private fun defineClass(
  name: String,
  evaluationContext: EvaluationContextImpl,
  classLoader: ClassLoaderReference?,
): Boolean {
  try {
    val rtJarClassLoader = URLClassLoader(arrayOf(ideaRtPath), null)
    rtJarClassLoader.getResourceAsStream("${name.replace('.', '/')}.class").use { stream ->
      if (stream == null) return false
      ClassLoadingUtils.defineClass(name, stream.readAllBytes(), evaluationContext, evaluationContext.debugProcess, classLoader)
      return true
    }
  }
  catch (ioe: IOException) {
    throw EvaluateException("Unable to read $name class bytes", ioe)
  }
}
