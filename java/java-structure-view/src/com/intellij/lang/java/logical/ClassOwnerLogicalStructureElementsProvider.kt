// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.logical

import com.intellij.ide.structureView.logical.LogicalStructureElementsProvider
import com.intellij.ide.structureView.logical.model.LogicalPsiDescription
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElement

@ApiStatus.Internal
class ClassOwnerLogicalStructureElementsProvider: LogicalStructureElementsProvider<PsiClassOwner, Any>, LogicalPsiDescription {
  override fun getElements(parent: PsiClassOwner): List<Any> {
    val result = mutableListOf<Any>()
    var convertedAtLeastOne = false
    for (psiClass in parent.classes) {
      if (!psiClass.isValid) continue
      val classModels = LogicalStructureElementsProvider.getProviders(psiClass)
        .filterIsInstance<PsiClassLogicalElementProvider<Any>>()
        .mapNotNull { it.convert(psiClass) }
        .toList()
      val methodModels = psiClass.methods.flatMap { method ->
        val providers = LogicalStructureElementsProvider.getProviders(method).toList()
        providers.flatMap { it.getElements(method) }
      }
      val allModels = classModels + methodModels
      convertedAtLeastOne = convertedAtLeastOne || allModels.isNotEmpty()
      result.addAll(allModels)
      if (allModels.isEmpty() && psiClass.identifyingElement != null) {
        result.add(psiClass)
      }
    }
    return if (convertedAtLeastOne) result else emptyList()
  }

  override fun getSuitableElement(psiElement: PsiElement): PsiElement? {
    if (psiElement is PsiClass) return psiElement
    return (psiElement.toUElement() as? UClass)?.javaPsi
  }

  override fun isAskChildren(): Boolean {
    return true
  }
}
