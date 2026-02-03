// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.localization

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.LocalizationFeedbackMessagesBundle"

internal object LocalizationFeedbackBundle {
  private val instance = DynamicBundle(LocalizationFeedbackBundle::class.java, BUNDLE)

  @Suppress("SpreadOperator")
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) = instance.getMessage(key, *params)
}