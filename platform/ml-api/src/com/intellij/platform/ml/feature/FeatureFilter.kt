// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.feature

import org.jetbrains.annotations.ApiStatus

/**
 * A filter that indicates whether a feature matches a feature set or not.
 * A feature filter functions within a particular tier.
 */
@ApiStatus.Internal
fun interface FeatureFilter {
  fun accept(featureDeclaration: FeatureDeclaration<*>): Boolean

  fun accept(featureDeclarations: Set<FeatureDeclaration<*>>): Set<FeatureDeclaration<*>> {
    return featureDeclarations.filter { accept(it) }.toSet()
  }

  companion object {
    val REJECT_ALL = FeatureFilter { false }
    val ACCEPT_ALL = FeatureFilter { true }

    fun FeatureFilter.inverted() = object : FeatureFilter {
      override fun accept(featureDeclaration: FeatureDeclaration<*>): Boolean {
        return !this@inverted.accept(featureDeclaration)
      }

      override fun accept(featureDeclarations: Set<FeatureDeclaration<*>>): Set<FeatureDeclaration<*>> {
        return featureDeclarations - this@inverted.accept(featureDeclarations)
      }
    }
  }
}
