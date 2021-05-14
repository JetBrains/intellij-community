// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.impl.ModifiableModelCommitterService
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModifiableModuleModelBridgeImpl
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class ModifiableModelCommitterServiceBridge : ModifiableModelCommitterService {
  override fun multiCommit(rootModels: MutableCollection<out ModifiableRootModel>, moduleModel: ModifiableModuleModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    // TODO Naive impl, check for existing contact in com.intellij.openapi.module.impl.ModuleManagerImpl.commitModelWithRunnable
    val diffs = mutableSetOf<WorkspaceEntityStorageBuilder>()
    diffs += (moduleModel as ModifiableModuleModelBridgeImpl).collectChanges()
    val committedModels = ArrayList<ModifiableRootModelBridge>()
    for (rootModel in rootModels) {
      if (rootModel.isChanged) {
        diffs += (rootModel as ModifiableRootModelBridgeImpl).collectChangesAndDispose() ?: continue
        committedModels += rootModel as ModifiableRootModelBridge
      }
      else rootModel.dispose()
    }

    WorkspaceModel.getInstance(moduleModel.project).updateProjectModel { builder ->
      diffs.forEach { builder.addDiff(it) }
    }

    for (rootModel in committedModels) {
      rootModel.postCommit()
    }
  }
}