// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import org.jetbrains.annotations.ApiStatus

object EssentialHighlightingMode {
  private val handle = RegistryManager.getInstance().get("ide.highlighting.mode.essential")

  fun isEnabled(): Boolean = handle.asBoolean()

  fun setEnabled(value: Boolean) {
    handle.setValue(value)
  }

  @ApiStatus.Internal
  fun isMyRegistryValue(value: RegistryValue) : Boolean {
    return value.key == handle.key
  }
}