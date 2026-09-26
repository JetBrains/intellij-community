// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery.actions

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Couple
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.ClassUtil
import com.intellij.rt.coverage.testDiscovery.instrumentation.TestDiscoveryInstrumentationUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TestDiscoveryMethodUtil {
  @JvmStatic
  fun getMethodKey(method: PsiMethod): Couple<String>? {
    if (DumbService.isDumb(method.project)) return null
    val containingClass = if (method.isValid) method.containingClass else null
    val fqn = containingClass?.let(DiscoveredTestsTreeModel::getClassName) ?: return null
    return Couple.of(fqn, methodSignature(method))
  }

  private fun methodSignature(method: PsiMethod): String {
    val tail = TestDiscoveryInstrumentationUtils.SEPARATOR + ClassUtil.getAsmMethodSignature(method)
    return (if (method.isConstructor) "<init>" else method.name) + tail
  }
}
