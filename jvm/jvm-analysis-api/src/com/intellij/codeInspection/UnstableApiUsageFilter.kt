// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Tells the `UnstableApiUsage` inspection to skip a usage of an unstable API.
 *
 * A plugin implements this extension point when an inspection of its own already reports the same usage.
 */
@ApiStatus.Internal
interface UnstableApiUsageFilter {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<UnstableApiUsageFilter> =
      ExtensionPointName("com.intellij.codeInsight.unstableApiUsageFilter")
  }

  /**
   * Returns `true` to skip the usage.
   *
   * @param usage the element that the inspection highlights.
   * @param annotationFqn the fully qualified name of the annotation that marks the used API as unstable.
   */
  fun isUsageIgnored(usage: PsiElement, annotationFqn: String): Boolean
}
