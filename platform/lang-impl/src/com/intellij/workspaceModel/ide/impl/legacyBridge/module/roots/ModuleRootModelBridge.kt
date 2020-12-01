// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.jps.model.module.JpsModuleSourceRoot

internal interface ModuleRootModelBridge: ModuleRootModel {
  val storage: WorkspaceEntityStorage
  val moduleBridge: ModuleBridge
  val accessor: RootConfigurationAccessor

  fun getOrCreateJpsRootProperties(sourceRootUrl: VirtualFileUrl, creator: () -> JpsModuleSourceRoot): JpsModuleSourceRoot
  fun removeCachedJpsRootProperties(sourceRootUrl: VirtualFileUrl)
}