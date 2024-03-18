// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface WorkspaceDataService<E> {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<WorkspaceDataService<*>> = ExtensionPointName.create("com.intellij.externalWorkspaceDataService")
  }

  /**
   * Returns the key of project data supported by the current data service.
   */
  fun getTargetDataKey(): Key<E>

  /**
   * It's assumed that given data nodes are present at the IDE when this method returns. The method should behave as follows for
   * each of the given data nodes:
   * - There is an existing project entity for the given data node and it has the same state. Do nothing for it then.
   * - There is an existing project entity for the given data node but it has a different state (e.g., a module dependency
   *   is configured as 'exported' at the IDE but not at the external system). Reset the state to the external system's one then.
   * - There is no corresponding project entity at the IDE side. Create it then.
   *
   * The data nodes are created, updated, or left as-is accordingly.
   */
  fun importData(toImport: Collection<DataNode<E>>,
                 projectData: ProjectData?,
                 project: Project,
                 mutableStorage: MutableEntityStorage) {
  }
}