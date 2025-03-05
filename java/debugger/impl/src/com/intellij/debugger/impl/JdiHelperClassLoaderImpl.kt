// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.registry.Registry
import com.sun.jdi.ClassType

private val HELPER_CLASS_CACHE_KEY = Key.create<HelperClassCache?>("HELPER_CLASS_CACHE_KEY")

private class JdiHelperClassLoaderImpl : JdiHelperClassLoader {
  @Throws(EvaluateException::class)
  override fun getHelperClass(
    cls: Class<*>, evaluationContext: EvaluationContextImpl,
    vararg additionalClassesToLoad: String,
  ): ClassType? {
    val vmProxy = evaluationContext.virtualMachineProxy
    val cache = vmProxy.getOrCreateUserData(HELPER_CLASS_CACHE_KEY) {
      HelperClassCache(evaluationContext.debugProcess, evaluationContext.managerThread)
    }
    val forceNewClassLoader = Registry.`is`("debugger.evaluate.load.helper.in.separate.classloader")
    return cache.getHelperClass(evaluationContext.withAutoLoadClasses(true), forceNewClassLoader, cls.name, *additionalClassesToLoad)
  }
}
