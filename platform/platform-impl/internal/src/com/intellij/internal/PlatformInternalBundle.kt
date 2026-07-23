// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE = "messages.PlatformInternalBundle"

@ApiStatus.Internal
object PlatformInternalBundle {
  private val instance = DynamicBundle(PlatformInternalBundle::class.java, BUNDLE)

  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return instance.getMessage(key, *params)
  }

  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<@Nls String> {
    return instance.getLazyMessage(key, *params)
  }
}
