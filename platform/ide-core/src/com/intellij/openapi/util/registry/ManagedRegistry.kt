// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface for implementation of provisioner that controls registry keys for teams.
 */
@ApiStatus.Internal
interface ManagedRegistry

private val EP_NAME = ExtensionPointName.create<ManagedRegistry>("com.intellij.registry.managed")

@ApiStatus.Internal
fun getManagedRegistry(): ManagedRegistry? {
  return EP_NAME.extensionList.firstOrNull()
}