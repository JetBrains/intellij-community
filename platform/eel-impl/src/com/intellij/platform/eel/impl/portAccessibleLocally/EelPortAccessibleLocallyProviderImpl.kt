// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.portAccessibleLocally

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.portAccessibleLocally.EelPortAccessibleLocally
import com.intellij.platform.eel.provider.portAccessibleLocally.EelPortAccessibleLocallyProvider
import org.jetbrains.annotations.ApiStatus

internal class EelPortAccessibleLocallyProviderImpl : EelPortAccessibleLocallyProvider {
  override suspend fun isEelPortAccessibleLocally(localPort: UShort, eelPort: UShort, onEel: EelDescriptor): Boolean =
    EelPortAccessibleLocallyEpBridge.EP_NAME.extensionList.any {
      it.isEelPortAccessibleLocally(localPort = localPort, eelPort = eelPort, onEel = onEel)
    }
}

@ApiStatus.Internal
object EelPortAccessibleLocallyEpBridge {
  val EP_NAME: ExtensionPointName<EelPortAccessibleLocally> = ExtensionPointName("com.intellij.platform.eel.impl.portAccessibleLocally")
}
