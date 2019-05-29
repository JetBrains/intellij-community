// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight

import com.intellij.codeInsight.javadoc.NonCodeAnnotationGenerator
import com.intellij.psi.PsiModifierListOwner

class ExternalAnnotationLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider() {
  override fun hasAnnotationsToShow(owner: PsiModifierListOwner): Boolean {
    return NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values().any { !it.isInferred }
  }

  override fun getName() = "External annotations"
}

class InferredAnnotationsLineMarkerProvider : NonCodeAnnotationsLineMarkerProvider() {
  override fun hasAnnotationsToShow(owner: PsiModifierListOwner): Boolean {
    val values = NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(owner).values()
    // Don't show two markers
    return values.any { it.isInferred } && values.none { !it.isInferred }
  }


  override fun getName() = "Inferred annotations"
}
