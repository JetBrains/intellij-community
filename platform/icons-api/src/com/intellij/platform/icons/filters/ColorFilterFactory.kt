// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.filters

import com.intellij.platform.icons.design.BlendMode
import com.intellij.platform.icons.design.Color
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ColorFilterFactory {
    fun tintColor(color: Color, blendMode: BlendMode): ColorFilter
}
