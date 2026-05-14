// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ijent.IjentRegistry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PlatformIjentRegistry : IjentRegistry {
  override fun isEnabled(key: String, defaultValue: Boolean): Boolean = Registry.`is`(key, defaultValue)
}
