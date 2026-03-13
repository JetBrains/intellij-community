// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.portAccessibleLocally

import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

// Helper for implementors to use typed descriptor
@ApiStatus.Internal
abstract class EelPortAccessibleLocallyTyped<T : EelDescriptor>(private val descType: KClass<T>) : EelPortAccessibleLocally {
  protected abstract suspend fun isEelPortAccessibleLocallyTyped(localPort: UShort, eelPort: UShort, onEel: T): Boolean

  override suspend fun isEelPortAccessibleLocally(localPort: UShort, eelPort: UShort, onEel: EelDescriptor): Boolean =
    if (descType.isInstance(onEel)) {
      @Suppress("UNCHECKED_CAST")
      isEelPortAccessibleLocallyTyped(localPort = localPort, eelPort = eelPort, onEel = onEel as T)
    }
    else {
      false
    }
}
