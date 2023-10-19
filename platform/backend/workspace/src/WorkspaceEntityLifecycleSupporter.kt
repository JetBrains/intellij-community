// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.annotations.ApiStatus

/**
 * This extension is designed to simplify handling entities.
 * It ensures on each project opening and each installation of plugin with [WorkspaceEntityLifecycleSupporter]
 * that the entities of class [E] are present in project's [WorkspaceModel] according to the result of [createSampleEntity],
 * i.e., there are none of them if it's null, and otherwise there is a single entity such that they [haveEqualData].
 *
 * Such a check may also be done with the help of [com.intellij.workspaceModel.ide.impl.WorkspaceEntityLifecycleSupporterUtils] methods
 */
@ApiStatus.OverrideOnly
@ApiStatus.Experimental
public interface WorkspaceEntityLifecycleSupporter<E : WorkspaceEntity> {
  public companion object {
    public val EP_NAME: ExtensionPointName<WorkspaceEntityLifecycleSupporter<out WorkspaceEntity>> =
      ExtensionPointName.create("com.intellij.workspaceModel.entityLifecycleSupporter")
  }

  public fun getEntityClass(): Class<E>

  /**
   * @return null if there should be no entities of class [E] in the project, or the entity that should be there
   *        That entity is not expected to be modified throughout whole life of the project unless plugin implementation has changed
   *
   *        The entity shouldn't be added to any store, just created.
   *        It would be added later automatically if such an entity is not added yet.
   */
  public fun createSampleEntity(project: Project): E?

  /**
   * Suitable equality for [E].
   * It is expected that `haveEqualData(createInitialEntity(project), createInitialEntity(project)) == true`
   */
  public fun haveEqualData(first: E, second: E): Boolean
}