// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface BridgeInitializer {
  fun isEnabled(): Boolean
  fun initializeBridges(project: Project, changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage)

  companion object {
    val EP_NAME: ExtensionPointName<BridgeInitializer> = ExtensionPointName("com.intellij.workspace.bridgeInitializer")
  }
}
