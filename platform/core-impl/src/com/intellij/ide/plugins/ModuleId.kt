// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus

/**
 * DO NOT use in API.
 */
@JvmInline
@ApiStatus.Internal
@IntellijInternalApi
value class ModuleId(val id: String)