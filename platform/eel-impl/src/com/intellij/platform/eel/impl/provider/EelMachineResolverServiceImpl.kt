// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.provider

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.provider.EelMachineResolver
import com.intellij.platform.eel.provider.EelMachineResolverProvider
import org.jetbrains.annotations.ApiStatus

internal class EelMachineResolverProviderImpl : EelMachineResolverProvider {
  override fun getAll(): List<EelMachineResolver> = EelMachineResolverEpBridge.EP_NAME.extensionList
}

@ApiStatus.Internal
object EelMachineResolverEpBridge {
  val EP_NAME: ExtensionPointName<EelMachineResolver> = ExtensionPointName("com.intellij.eelMachineResolver")
}
