// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor.completion

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

internal object CompletionMessageBundle {
  private const val BUNDLE = "messages.CompletionBundle"
  private val INSTANCE = DynamicBundle(CompletionMessageBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(@NotNull @PropertyKey(resourceBundle = BUNDLE) key: String, @NotNull vararg params: Any?): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }

  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): Supplier<String> {
    return INSTANCE.getLazyMessage(key, *params)
  }
}