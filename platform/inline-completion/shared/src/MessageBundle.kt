// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.PropertyKey

internal object MessageBundle {
  private const val BUNDLE = "messages.InlineCompletionBundle"
  private val INSTANCE = DynamicBundle(MessageBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(@NotNull @PropertyKey(resourceBundle = BUNDLE) key: String, @NotNull vararg params: Any): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }
}