// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ml

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MLFeature {
  val name: String
  fun compute(): MLFeatureValueBase

  companion object {
    fun simple(name: String, value: MLFeatureValueBase): MLFeature = object : MLFeature {
      override val name: String = name
      override fun compute(): MLFeatureValueBase = value
    }
  }
}
