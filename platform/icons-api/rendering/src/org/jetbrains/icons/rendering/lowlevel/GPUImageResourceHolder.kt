// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering.lowlevel

import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

@ApiStatus.Internal
interface GPUImageResourceHolder {
  fun <TBitmap : Any> getOrGenerateBitmap(bitmapClass: KClass<TBitmap>, generator: () -> TBitmap): TBitmap
}
