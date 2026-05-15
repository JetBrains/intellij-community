// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object IjentCommunityImplBundle {
  private const val BUNDLE: @NonNls String = "messages.IjentCommunityImplBundle"
  private val INSTANCE = DynamicBundle(IjentCommunityImplBundle::class.java, BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String =
    INSTANCE.getMessage(key, *params)
}