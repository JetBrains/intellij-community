// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*
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
 *
 * With any option suitable, the resulting class loader will always be used for the current class loader.
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

  private fun launchCleaningTask(managerThread: DebuggerManagerThreadImpl) {
    managerThread.coroutineScope.launch {
      while (true) {
        delay(5.seconds)
        withDebugContext(managerThread) {
          val collectedClassLoaders = evaluationClassLoaderMapping.keys.filter { it?.isCollected == true }
          for (classLoader in collectedClassLoaders) {
            val info = evaluationClassLoaderMapping.remove(classLoader) ?: continue
            if (info is ClassLoaderInfo.DefinedInCompanionClassLoader) {
              DebuggerUtilsImpl.enableCollection(info.classLoader)
            }
          }
        }
      }
    }
  }

  private fun releaseAllClassLoaders() {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    val createdLoaders = evaluationClassLoaderMapping.values.filterIsInstance<ClassLoaderInfo.DefinedInCompanionClassLoader>()
    for (info in createdLoaders) {
      DebuggerUtilsImpl.enableCollection(info.classLoader)
    }
    evaluationClassLoaderMapping.clear()
  }

  fun getHelperClass(
    evaluationContext: EvaluationContextImpl, forceNewClassLoader: Boolean,
    cls: Class<*>, vararg additionalClassesToLoad: String,
  ): ClassType? {
    val currentClassLoader = evaluationContext.classLoader
    val classLoaderInfo = evaluationClassLoaderMapping[currentClassLoader]
    return when (classLoaderInfo) {
      is ClassLoaderInfo.LoadFailedMarker -> null
      is ClassLoaderInfo.DefinedInCompanionClassLoader? -> {
        loadHelperClassWithClassLoaderCaching(classLoaderInfo, evaluationContext, forceNewClassLoader, cls, *additionalClassesToLoad)
      }
    }
  }

  private fun loadHelperClassWithClassLoaderCaching(
    currentInfo: ClassLoaderInfo.DefinedInCompanionClassLoader?, evaluationContext: EvaluationContextImpl,
    forceNewClassLoader: Boolean, cls: Class<*>, vararg additionalClassesToLoad: String,
  ): ClassType? {
    val preferNewClassLoader = forceNewClassLoader || currentInfo != null
    val currentClassLoader = evaluationContext.classLoader
    if (!preferNewClassLoader) {
      val type = tryLoadInClassLoader(evaluationContext, cls.name, currentClassLoader)
      if (type != null) return type
    }
    if (!preferNewClassLoader && currentClassLoader != null) {
      val parentClassLoader = getTopClassloader(evaluationContext, currentClassLoader)
      val type = tryToDefineInClassLoader(evaluationContext, parentClassLoader, currentClassLoader, cls, *additionalClassesToLoad)
      if (type != null) return type
    }
    val companionClassLoader = currentInfo?.classLoader
                               ?: ClassLoadingUtils.getClassLoader(evaluationContext, evaluationContext.debugProcess)
    val type = tryToDefineInClassLoader(evaluationContext, companionClassLoader, companionClassLoader, cls, *additionalClassesToLoad)
    if (type != null) {
      if (currentInfo == null) {
        DebuggerUtilsImpl.disableCollection(companionClassLoader)
        evaluationClassLoaderMapping[currentClassLoader] = ClassLoaderInfo.DefinedInCompanionClassLoader(companionClassLoader)
      }
      return type
    }
    return null
  }

  private fun tryToDefineInClassLoader(
    evaluationContext: EvaluationContextImpl,
    loaderForDefine: ClassLoaderReference, loaderForLookup: ClassLoaderReference,
    cls: Class<*>, vararg additionalClassesToLoad: String,
  ): ClassType? {
    val alreadyDefined = tryLoadInClassLoader(evaluationContext, cls.name, loaderForDefine)
    if (alreadyDefined == null) {
      for (className in listOf(cls.name, *additionalClassesToLoad)) {
        if (!defineClass(className, cls, evaluationContext, loaderForDefine)) return null
      }
    }
    return tryLoadInClassLoader(evaluationContext, cls.name, loaderForLookup)
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

private fun defineClass(
  name: String,
  cls: Class<*>,
  evaluationContext: EvaluationContextImpl,
  classLoader: ClassLoaderReference?,
): Boolean {
  try {
    cls.getResourceAsStream("/${name.replace('.', '/')}.class").use { stream ->
      if (stream == null) return false
      ClassLoadingUtils.defineClass(name, stream.readAllBytes(), evaluationContext, evaluationContext.debugProcess, classLoader)
      return true
    }
  }
  catch (ioe: IOException) {
    throw EvaluateException("Unable to read $name class bytes", ioe)
  }
}
