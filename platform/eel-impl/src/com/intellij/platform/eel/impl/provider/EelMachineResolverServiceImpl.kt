// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.provider

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelMachineConnectionProvider
import com.intellij.platform.eel.provider.EelMachineResolver
import org.jetbrains.annotations.ApiStatus

internal class EelMachineResolverProviderImpl : EelMachineResolver.Provider {
  override fun getAll(): List<EelMachineResolver> {
    return EelMachineResolverEpBridge.EP_NAME.extensionList
  }
}

@ApiStatus.Internal
object EelMachineResolverEpBridge {
  val EP_NAME: ExtensionPointName<EelMachineResolver> = ExtensionPointName("com.intellij.eelMachineResolver")
}

internal class EelMachineConnectionProviderBridgeImpl : EelMachineConnectionProvider.Bridge {
  override fun getAll(): List<EelMachineConnectionProvider> {
    return EelMachineConnectionProviderEpBridge.EP_NAME.extensionList
  }
}

@ApiStatus.Internal
object EelMachineConnectionProviderEpBridge {
  val EP_NAME: ExtensionPointName<EelMachineConnectionProvider> = ExtensionPointName("com.intellij.eelMachineConnectionProvider")
}
