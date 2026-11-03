// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus

/** Matches PSI methods to JVM signatures and declaring classes. */
@ApiStatus.Internal
object JvmMethodMatcher {
  private val LOG = Logger.getInstance(JvmMethodMatcher::class.java)

  @JvmStatic
  fun matches(psiMethod: PsiMethod, className: String, name: String?, signature: String, process: DebugProcessImpl): Boolean {
    val methodInfo = runReadActionBlocking { getMethodInfo(psiMethod) }
    if (methodInfo.containingClass == null || name != null && name != methodInfo.name) return false
    return methodInfo.resolve(process).matches(className, name, signature, process) { methodClassName ->
      VirtualMachineProxy.getCurrent().classesByName(className).any { DebuggerUtils.instanceOf(it, methodClassName) }
    }
  }

  @JvmStatic
  @RequiresReadLock
  fun getMethodInfo(method: PsiMethod): MethodInfo {
    val containingClass = method.containingClass
    return MethodInfo(containingClass, JVMNameUtil.getJVMMethodName(method),
                      JVMNameUtil.getClassVMName(containingClass), JVMNameUtil.getJVMSignature(method))
  }

  data class MethodInfo(
    val containingClass: PsiClass?,
    val name: String,
    val className: String?,
    val signature: JVMName,
  ) {
    @RequiresReadLockAbsence
    fun resolve(process: DebugProcessImpl): ResolvedMethodInfo {
      val resolvedSignature = try {
        signature.getName(process)
      }
      catch (e: EvaluateException) {
        LOG.debug(e)
        null
      }
      return ResolvedMethodInfo(this, resolvedSignature)
    }
  }

  data class ResolvedMethodInfo(val method: MethodInfo, val signature: String?) {
    fun matches(
      className: String,
      name: String?,
      signature: String,
      process: DebugProcessImpl,
      runtimeClassMatches: (String) -> Boolean,
    ): Boolean {
      if (name != null && name != method.name || signature != this.signature) return false
      val methodClassName = method.className
      if (methodClassName == className) return true
      if (methodClassName == null) return false
      if (runtimeClassMatches(methodClassName)) return true
      return runReadActionBlocking {
        val containingClass = method.containingClass
        if (containingClass == null || !containingClass.isValid) return@runReadActionBlocking false
        val aClass = PositionManagerImpl.findClass(process.project, className, process.searchScope, true)
        aClass != null && aClass.isInheritor(containingClass, true)
      }
    }
  }
}
