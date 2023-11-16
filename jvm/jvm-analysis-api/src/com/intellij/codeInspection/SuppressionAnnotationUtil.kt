// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UExpression

private val EP_NAME: ExtensionPointName<SuppressionAnnotationUtil> =
  ExtensionPointName.create("com.intellij.codeInspection.suppressionAnnotationUtil")

/**
 * Helper extension providing utility methods used by [com.intellij.codeInspection.SuppressionAnnotationInspection].
 */
interface SuppressionAnnotationUtil {
  companion object {
    @JvmField
    val extension = LanguageExtension<SuppressionAnnotationUtil>(EP_NAME.name)
  }

  /**
   * @return true if a given annotation is a suppression annotation in its language,
   * e.g. [SuppressWarnings] in Java or [Suppress] in Kotlin.
   */
  fun isSuppressionAnnotation(annotation: UAnnotation): Boolean

  /**
   * @return values of a suppression annotation attribute defining suppressed problem names.
   * Returned values should be [org.jetbrains.uast.UNamedExpression.expression].
   */
  fun getSuppressionAnnotationAttributeExpressions(annotation: UAnnotation): List<UExpression>
}
