// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.containers.Interner
import org.jetbrains.annotations.ApiStatus

/**
 * DO NOT use in API.
 */
@JvmInline
@ApiStatus.Internal
@IntellijInternalApi
value class ModuleId private constructor(val id: String){
  companion object {
    // ModuleId can be either boxed or unboxed, so only interning of value matters
    private val interner = Interner.createWeakInterner<String>()

    operator fun invoke(id: String): ModuleId = ModuleId(interner.intern(id))
  }
}