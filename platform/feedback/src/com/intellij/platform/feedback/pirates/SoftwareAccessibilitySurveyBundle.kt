// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.pirates

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.SoftwareAccessibilitySurveyBundle"

internal object SoftwareAccessibilitySurveyBundle {
  private val bundle = DynamicBundle(this::class.java, BUNDLE)

  @Suppress("SpreadOperator")
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) = bundle.getMessage(key, *params)
}