// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader

/**
 * Registry lookup for IJent.
 *
 * Mirrors `com.intellij.openapi.util.registry.Registry` but lives in the small core `intellij.platform.ijent`
 * module so the base does not have to depend on `intellij.platform.util`. The platform-backed implementation
 * lives in `intellij.platform.ijent.community.impl` and is plugged in via `ServiceLoader`. When no implementation
 * is on the classpath, all flags default to `false` if boolean and `0` if integer.
 */
@ApiStatus.Internal
interface IjentRegistry {
  fun isEnabled(key: String, defaultValue: Boolean = false): Boolean

  fun intValue(key: String, defaultValue: Int = 0): Int

  companion object {
    private val cached: IjentRegistry by lazy {
      ServiceLoader
        .load(IjentRegistry::class.java, IjentRegistry::class.java.classLoader)
        .firstOrNull()
      ?: DefaultIjentRegistry
    }

    @JvmStatic
    fun getInstance(): IjentRegistry = cached
  }
}

private object DefaultIjentRegistry : IjentRegistry {
  override fun isEnabled(key: String, defaultValue: Boolean): Boolean = defaultValue

  override fun intValue(key: String, defaultValue: Int): Int = defaultValue
}
