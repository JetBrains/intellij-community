// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.platform.ijent.community.buildConstants.IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY

internal class IjentWslNioFsRegistryListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if (value.key == IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY) {
      IjentWslNioFsVmOptionsSetter.ensureInVmOptions()
    }
  }
}