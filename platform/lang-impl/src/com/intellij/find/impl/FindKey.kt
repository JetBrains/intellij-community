// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object FindKey {
  val isEnabled: Boolean
    @JvmStatic
    get() = Registry.`is`("find.in.files.split")
}