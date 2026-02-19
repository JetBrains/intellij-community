// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

@ApiStatus.Internal
object CoreBundle {
  private const val BUNDLE: String = "messages.CoreBundle"
  private val INSTANCE = DynamicBundle(CoreBundle::class.java, BUNDLE)

  @Nls
  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any?): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }

  @Nls
  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String): () -> String {
    return { INSTANCE.getMessage(key) }
  }

  @Nls
  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, param: String): () -> String {
    return { INSTANCE.getMessage(key, param) }
  }

  @Nls
  @JvmStatic
  fun messageOrNull(
    key: @PropertyKey(resourceBundle = BUNDLE) String,
    vararg params: Any
  ): @Nls String? {
    return INSTANCE.messageOrNull(key, *params)
  }
}
