// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.ml

interface MLFeature {
  val name: String
  fun compute(): MLFeatureValue
}

data class SimpleMLFeature(override val name: String, private val value: MLFeatureValue) : MLFeature {
  override fun compute(): MLFeatureValue = value
}
