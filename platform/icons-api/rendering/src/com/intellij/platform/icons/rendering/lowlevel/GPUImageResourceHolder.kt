// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering.lowlevel

import kotlin.reflect.KClass
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GPUImageResourceHolder {
    fun <TBitmap : Any> getOrGenerateBitmap(bitmapClass: KClass<TBitmap>, generator: () -> TBitmap): TBitmap
}
