// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace.impl

import com.intellij.platform.backend.workspace.BuilderSnapshot
import com.intellij.platform.backend.workspace.StorageReplacement
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * This interface is created only for internal use.
 */
@ApiStatus.Internal
public interface WorkspaceModelInternal: WorkspaceModel {
  public val entityStorage: VersionedEntityStorageImpl

  /**
   * Returns a snapshot of the storage containing unloaded entities.
   * Unloaded entities must be ignored by almost all code in the IDE, so this property isn't supposed for general use.
   *
   * Currently, unloaded entities correspond to modules which are unloaded using 'Load/Unload Modules' action.
   */
  public val currentSnapshotOfUnloadedEntities: ImmutableEntityStorage

  /**
   * Modifies the current model of unloaded entities by calling [updater] and applying it to the storage.
   * @param description describes the reason for the change, used for logging purposes only.
   */
  public fun updateUnloadedEntities(description: @NonNls String, updater: (MutableEntityStorage) -> Unit)

  /**
   * Get builder that can be updated in background and applied later and a project model.
   *
   * @see [WorkspaceModel.replaceProjectModel]
   */
  public fun getBuilderSnapshot(): BuilderSnapshot

  /**
   * Replace current project model with the new version from storage snapshot.
   *
   * This operation requires write lock.
   * The snapshot replacement is performed using positive lock. If the project model was updated since [getBuilderSnapshot], snapshot
   *   won't be applied and this method will return false. In this case client should get a newer version of snapshot builder, apply changes
   *   and try to call [replaceProjectModel].
   *   Keep in mind that you may not need to start the full builder update process (e.g. gradle sync) and the newer version of the builder
   *   can be updated using [MutableEntityStorage.applyChangesFrom] or [MutableEntityStorage.replaceBySource], but you have to be
   *   sure that the changes will be applied to the new builder correctly.
   *
   * The calculation of changes will be performed during [BuilderSnapshot.getStorageReplacement]. This method only replaces the project model
   *   and sends corresponding events.
   *
   * Example:
   * ```
   *   val builderSnapshot = projectModel.getBuilderSnapshot()
   *
   *   update(builderSnapshot)
   *
   *   val storageSnapshot = builderSnapshot.getStorageReplacement()
   *   val updated = writeLock { projectModel.replaceProjectModel(storageSnapshot) }
   *
   *   if (!updated) error("Project model updates too fast")
   * ```
   *
   * Future plans: add some kind of ordering for async updates of the project model
   *
   * @see [WorkspaceModel.getBuilderSnapshot]
   */
  public fun replaceProjectModel(replacement: StorageReplacement): Boolean
}

@get:ApiStatus.Internal
public val WorkspaceModel.internal: WorkspaceModelInternal get() = this as WorkspaceModelInternal