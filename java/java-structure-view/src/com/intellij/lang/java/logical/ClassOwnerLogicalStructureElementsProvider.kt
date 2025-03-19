// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.logical

import com.intellij.ide.structureView.logical.LogicalStructureElementsProvider
import com.intellij.psi.PsiClassOwner
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ClassOwnerLogicalStructureElementsProvider: LogicalStructureElementsProvider<PsiClassOwner, Any> {
  override fun getElements(parent: PsiClassOwner): List<Any> {
    val result = mutableListOf<Any>()
    var convertedAtLeastOne = false
    for (psiClass in parent.classes) {
      if (!psiClass.isValid) continue
      val convertedModels = LogicalStructureElementsProvider.getProviders(psiClass)
        .filterIsInstance<PsiClassLogicalElementProvider<Any>>()
        .mapNotNull { it.convert(psiClass) }
        .toList()
      if (convertedModels.count() > 0) {
        convertedModels.forEach { result.add(it) }
        convertedAtLeastOne = true
      }
      else if (psiClass.identifyingElement != null) {
        result.add(psiClass)
      }
    }
    if (convertedAtLeastOne) return result
    return emptyList()
  }
}
