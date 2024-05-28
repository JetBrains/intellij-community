// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener

internal class IjentWslNioFsRegistryListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if (value == Registry.get("wsl.use.remote.agent.for.nio.filesystem")) {
      service<IjentWslNioFsToggler>().ensureInVmOptions()
    }
  }
}