// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.provider

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.impl.base.EelEnvironmentHousekeepingService
import com.intellij.platform.eel.impl.base.EelEnvironmentHousekeepingServiceProvider
import org.jetbrains.annotations.ApiStatus


internal class EelEnvironmentHousekeepingServiceProviderImpl : EelEnvironmentHousekeepingServiceProvider {
  override fun getAll(): List<EelEnvironmentHousekeepingService> = IjentEnvironmentServiceProviderImplBridge.EP_NAME.extensionList
}

@ApiStatus.Internal
object IjentEnvironmentServiceProviderImplBridge {
  val EP_NAME: ExtensionPointName<EelEnvironmentHousekeepingService> = ExtensionPointName("com.intellij.eelEnvironment")
}
