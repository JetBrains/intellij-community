// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus

object LegacyBridgeTestFrameworkUtils {
  @ApiStatus.Internal
  fun dropCachesOnTeardown(project: Project) {
    if (!LegacyBridgeProjectLifecycleListener.enabled(project)) {
      return
    }

    WriteAction.runAndWait<RuntimeException> {
      for (module in ModuleManager.getInstance(project).modules) {
        (ModuleRootManager.getInstance(module) as ModuleRootComponentBridge).dropCaches()
      }
    }
  }
}