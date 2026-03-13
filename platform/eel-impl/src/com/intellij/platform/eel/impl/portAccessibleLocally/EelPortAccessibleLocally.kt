// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.portAccessibleLocally

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * For Eel implementation only, do not use
 */
@ApiStatus.Internal
interface EelPortAccessibleLocally {
  companion object {
    private val EP = ExtensionPointName.create<EelPortAccessibleLocally>("com.intellij.platform.eel.impl.portAccessibleLocally")
    internal suspend fun isEelPortAccessibleLocally(localPort: UShort, eelPort: UShort, onEel: EelDescriptor): Boolean =
      EP.extensionList.any { it.isEelPortAccessibleLocally(localPort = localPort, eelPort = eelPort, onEel = onEel) }
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
