// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.demo

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.DemoFeedbackMessagesBundle"

// todo move demos to DevKit
internal object DemoFeedbackBundle {
  private val instance = DynamicBundle(DemoFeedbackBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return instance.getMessage(key, *params)
  }
}