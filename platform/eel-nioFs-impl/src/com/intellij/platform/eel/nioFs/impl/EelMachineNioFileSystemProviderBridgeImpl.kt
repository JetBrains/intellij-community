// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.nioFs.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelMachineNioFileSystemProvider
import org.jetbrains.annotations.ApiStatus

internal class EelMachineNioFileSystemProviderBridgeImpl : EelMachineNioFileSystemProvider.Bridge {
  override fun getAll(): List<EelMachineNioFileSystemProvider> {
    return EelMachineNioFileSystemProviderEpBridge.EP_NAME.extensionList
  }
}

@ApiStatus.Internal
object EelMachineNioFileSystemProviderEpBridge {
  val EP_NAME: ExtensionPointName<EelMachineNioFileSystemProvider> = ExtensionPointName("com.intellij.eelMachineNioFileSystemProvider")
}
