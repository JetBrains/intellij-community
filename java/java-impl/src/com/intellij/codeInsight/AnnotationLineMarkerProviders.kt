// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight

import com.intellij.codeInsight.javadoc.AnnotationDocGenerator
import com.intellij.codeInsight.javadoc.NonCodeAnnotationGenerator
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.annotations.Contract

private val AnnotationDocGenerator.isContract: Boolean
  get() = targetClass?.qualifiedName == Contract::class.java.name

class ExternalAnnotationLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider() {
  override fun hasAnnotationsToShow(owner: PsiModifierListOwner): Boolean {
    return NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values().any { !it.isInferred }
  }

  override fun getName() = "External annotations"
}

class InferredNullabilityAnnotationsLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider() {
  override fun hasAnnotationsToShow(owner: PsiModifierListOwner): Boolean {
    val values = NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values()
    // Don't show two markers
    return values.any { it.isInferred && !it.isContract } && values.none { !it.isInferred }
  }

  override fun getName() = "Inferred nullability annotations"
}

class InferredContractAnnotationsLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider() {
  override fun hasAnnotationsToShow(owner: PsiModifierListOwner): Boolean {
    val values = NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values()
    return values.any { it.isInferred && it.isContract } &&
           values.none { it.isInferred && !it.isContract } /* don't show when there's a marker for inferred nullability */ &&
           values.none { !it.isInferred } // don't show when there's a marker for external
  }

  override fun isEnabledByDefault() = false

  override fun getName() = "Inferred contract annotations"
}
