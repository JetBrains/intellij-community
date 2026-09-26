// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.portAccessibleLocally

import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader

/**
 * For Eel implementation only, do not use
 */
@ApiStatus.Internal
interface EelPortAccessibleLocally {
  companion object {
    private val provider: EelPortAccessibleLocallyProvider? by lazy {
      ServiceLoader.load(EelPortAccessibleLocallyProvider::class.java, EelPortAccessibleLocallyProvider::class.java.classLoader).firstOrNull()
    }

    @ApiStatus.Internal
    suspend fun isEelPortAccessibleLocally(localPort: UShort, eelPort: UShort, onEel: EelDescriptor): Boolean =
      provider?.isEelPortAccessibleLocally(localPort = localPort, eelPort = eelPort, onEel = onEel) ?: false
  }

  /**
   * When IJ connects to [localPort] it automatically gets connected to [eelPort] on [onEel]
   * - WSL Mirrored Network
   * - Docker host mode
   * - Docker mapped ports (`8080:80`)
   * are some of these cases
   */
  suspend fun isEelPortAccessibleLocally(localPort: UShort, eelPort: UShort, onEel: EelDescriptor): Boolean
}

/**
 * SPI loaded via [ServiceLoader] from `eel-provider`. Bridges [EelPortAccessibleLocally]'s companion
 * to platform extension-point discovery so that `eel` doesn't need a compile-time edge to the
 * `intellij.platform.extensions` framework.
 */
@ApiStatus.Internal
fun interface EelPortAccessibleLocallyProvider {
  suspend fun isEelPortAccessibleLocally(localPort: UShort, eelPort: UShort, onEel: EelDescriptor): Boolean
}
