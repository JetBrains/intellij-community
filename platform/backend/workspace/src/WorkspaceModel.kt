// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Provides access to [the storage](psi_element://com.intellij.platform.workspace.storage) with entities describing the workspace model
 * inside the IDE process.
 * 
 * Use [currentSnapshot] to read the current state of the model. 
 * There are several ways to modify the model:
 * * [updateProjectModel] updates the model synchronously under write-lock; it should be used for small modifications;
 * * [replaceProjectModel] updates the model synchronously using a given replacement, which may be prepared in advance on a background 
 * thread; it should be used for major modifications to minimize time spent under write-lock;
 * * [update] schedules asynchronous update of the model; currently the actual update is performed with a write-lock in a blocking manner,
 * however, this will change in the future; this function is experimental for now, it can be used from suspending Kotlin functions.
 * 
 * In order to subscribe to changes in the workspace model, use [changesEventFlow] to process changes in an asynchronous manner. If you need
 * to process them synchronously, you may subscribe to [WorkspaceModelTopics.CHANGED] topic.
 * 
 * Instance of this interface can be obtained via [workspaceModel] extension property.
 * 
 * To provide interoperability with code which use the old project model API ([ModuleManager][com.intellij.openapi.module.ModuleManager],
 * [ModuleRootManager][com.intellij.openapi.roots.ModuleRootManager], [Library][com.intellij.openapi.roots.libraries.Library], etc.),
 * types of workspace entities corresponding to the old concepts are defined in [com.intellij.platform.workspace.jps.entities](psi_element://com.intellij.platform.workspace.jps.entities)
 * package. Implementations of old interfaces (so-called 'legacy bridges') use entities of these types to store data.
 */
public interface WorkspaceModel {
  /**
   * Returns snapshot of the workspace model storage. 
   * The returned value won't be affected by future changes in [WorkspaceModel], so it can be safely used without any locks from any thread.
   */
  public val currentSnapshot: ImmutableEntityStorage

  /**
   * Flow of changes from workspace model. It has to be used for asynchronous event handling. To start receiving
   * emitted events, you need to call one of the terminal operations on it.
   *
   * This can be used as a direct migration from [WorkspaceModelChangeListener] to the non-blocking and non-write-action
   *   approach. This flow will eventually become obsolete as we'll present more handy listeners for the workspace model.
   *
   * See the documentation of [WorkspaceModelChangeListener] for details about changes collecting.
   */
  @get:ApiStatus.Experimental
  public val changesEventFlow: Flow<VersionedStorageChange>

  /**
   * Returns a snapshot of the storage containing unloaded entities. 
   * Unloaded entities must be ignored by almost all code in the IDE, so this property isn't supposed for general use.
   * 
   * Currently, unloaded entities correspond to modules which are unloaded using 'Load/Unload Modules' action. 
   */
  public val currentSnapshotOfUnloadedEntities: ImmutableEntityStorage

  /**
   * Modifies the current model by calling [updater] and applying it to the storage. Requires write action.
   *
   * Use [description] to briefly describe what do you update. This message will be logged and can be used for debugging purposes.
   *   For testing there is an extension method that doesn't require a description [com.intellij.testFramework.workspaceModel.updateProjectModel].
   */
  public fun updateProjectModel(description: @NonNls String, updater: (MutableEntityStorage) -> Unit)

  /**
   * Modifies the current model of unloaded entities by calling [updater] and applying it to the storage.
   * @param description describes the reason for the change, used for logging purposes only.
   */
  public fun updateUnloadedEntities(description: @NonNls String, updater: (MutableEntityStorage) -> Unit)

  /**
   * **Asynchronous** modification of the current model by calling [updater] and applying it to the storage.
   *
   * Use [description] to briefly describe what do you update. This message will be logged and can be used for debugging purposes.
   */
  @ApiStatus.Experimental
  public suspend fun update(description: @NonNls String, updater: (MutableEntityStorage) -> Unit)

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
   *   can be updated using [MutableEntityStorage.addDiff] or [MutableEntityStorage.replaceBySource], but you have to be
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

  public companion object {
    @JvmStatic
    public fun getInstance(project: Project): WorkspaceModel = project.service()
  }
}

/**
 * A shorter variant of [WorkspaceModel.getInstance].
 */
public val Project.workspaceModel: WorkspaceModel
  get() = WorkspaceModel.getInstance(this)
