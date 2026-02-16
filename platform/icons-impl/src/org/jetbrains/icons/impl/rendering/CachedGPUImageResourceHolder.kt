// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.rendering.lowlevel.GPUImageResourceHolder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass

open class CachedGPUImageResourceHolder: GPUImageResourceHolder {
  private val cache: ConcurrentMap<KClass<*>, Any> = ConcurrentHashMap()

  override fun <TBitmap : Any> getOrGenerateBitmap(bitmapClass: KClass<TBitmap>, generator: () -> TBitmap): TBitmap {
    val bitmap = cache.computeIfAbsent(bitmapClass) { generator() }
    if (bitmap == null || !bitmapClass.isInstance(bitmap)) error("Unexpected type of cached bitmap")
    @Suppress("UNCHECKED_CAST")
    return bitmap as TBitmap
  }
}