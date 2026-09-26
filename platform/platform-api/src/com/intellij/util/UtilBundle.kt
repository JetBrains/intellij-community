// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

@Internal
object UtilBundle {
  private const val BUNDLE_NAME = "messages.UtilBundle"
  private val bundle = DynamicBundle(UtilBundle::class.java, BUNDLE_NAME)

  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): String = bundle.getMessage(key, *params)
}