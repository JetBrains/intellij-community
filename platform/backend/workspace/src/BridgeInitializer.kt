// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

/**
 * This interface represents a bridge initializer which is responsible for creating bridges
 * base on the changes in project level WorkspaceModel
 */
@ApiStatus.Internal
public interface BridgeInitializer {
  public fun isEnabled(): Boolean
  public fun initializeBridges(project: Project, changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage)

  public companion object {
    @JvmField
    public val EP_NAME: ExtensionPointName<BridgeInitializer> = ExtensionPointName("com.intellij.workspace.bridgeInitializer")
  }
}
