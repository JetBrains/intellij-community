// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE_FQN = "messages.IjentBundle"

object IjentBundle {
  private val BUNDLE = DynamicBundle(IjentBundle::class.java, BUNDLE_FQN)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE_FQN) String, vararg params: Any): @Nls String =
    BUNDLE.getMessage(key, *params)
}