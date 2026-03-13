// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import com.intellij.platform.eel.impl.portAccessibleLocally.EelPortAccessibleLocallyTyped

internal class WslEelPortAccessibleLocally : EelPortAccessibleLocallyTyped<WslEelDescriptor>(WslEelDescriptor::class) {
  override suspend fun isEelPortAccessibleLocallyTyped(
    localPort: UShort,
    eelPort: UShort,
    onEel: WslEelDescriptor,
  ): Boolean = localPort == eelPort && isMirroredMode(desc = onEel, eelApi = null)
}
