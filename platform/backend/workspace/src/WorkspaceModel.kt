// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Obsolete
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
 * In order to subscribe to changes in the workspace model, use [eventLog] to process changes in an asynchronous manner.
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
   * Modifies the current model by calling [updater] and applying it to the storage. Requires write action.
   *
   * Use [description] to briefly describe what you update. This message will be logged and can be used for debugging purposes.
   *   For testing there is an extension method that doesn't require a description [com.intellij.testFramework.workspaceModel.updateProjectModel].
   *
   * **N.B** This method has to be used only in the place where compatibility with the old codebase is needed, e.g. old contracts that rely
   * on synchronous data modification executed under WA. For all other proposes and for the newly written code, use [update]
   */
  @Obsolete
  @RequiresWriteLock
  public fun updateProjectModel(description: @NonNls String, updater: (MutableEntityStorage) -> Unit)

  /**
   * **Asynchronous** modification of the current model by calling [updater] and applying it to the storage. Has to be called outside any locks
   *
   * Use [description] to briefly describe what you update. This message will be logged and can be used for debugging purposes.
   *
   * **N.B** There is no guarantee that after the execution, all *Bridges(that used for the compatibility with the old codebase) will be
   * up-to-date and [WorkspaceFileIndex][com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex] will be updated.
   * So the client code should not rely on this.
   */
  public suspend fun update(description: @NonNls String, updater: (MutableEntityStorage) -> Unit)

  /**
   * Flow of workspace model changes.
   *
   * This flow can be used in two ways:
   * - Perform incremental computation. Calculate information based on the initial storage and update this info according to the updates.
   * - Receive events of the workspace model updates.
   *
   * **If the subscription should be set up after application start**, the [ProjectActivity][com.intellij.openapi.startup.ProjectActivity]
   *   can be used. This is also the way to migrate from [WorkspaceModelChangeListener] to this subscription. Keep in mind that
   *   such an approach may miss a few first updates of the model, what is normal.
   *
   * Keep in mind that since this listener is asynchronous, there is no guarantee that file index or jps bridges will be updated.
   *
   * # Incremental computation
   *
   * To make the incremental computations, you'll need the last known version of the storage and the event of updates. This can be
   *   obtained by collecting indexed events. Let's assume we build some index and keep it updated.
   *  ```kotlin
   *  workspaceModel.eventLog.collectIndexed { index, event ->
   *    if (index == 0) {
   *      buildIndex(event.storageAfter)
   *    } else {
   *      updateIndex(event)
   *    }
   *  }
   *  ```
   *
   * # Behavior details
   *
   * The [EntityChange.Added] and [EntityChange.Removed] events are straightforward and generated in case of added or removed entities.
   *
   * The [EntityChange.Replaced] is generated in case if any of the fields of the entity changes the value in the newer
   *   version of storage.
   * This means that this event is generated in two cases: "primitive" field change (Int, String, data class, etc.) or
   *   changes of the references to other entities. The change of references may happen indirectly by modifying the referred entity.
   *   For example, if we remove child entity, we'll generate two events: remove for child and replace for parent.
   *                if we add a new child entity, we'll also generate two events: add for child and replace for parent.
   *
   * ## Examples
   *
   * Assuming the following structure of entities: A --> B --> C
   * Where A is the root entity and B and C are the children.
   *
   * - If we modify the primitive field of C: [Replace(C)]
   * - If we remove C: [Replace(B), Remove(C)]
   * - If we remove reference between B and C: [Replace(B), Replace(C)]
   * - If we remove B: [Replace(A), Remove(B), Remove(C)] - C is cascade removed
   *
   * Another example:
   * Before: A --> B  C, After A  C --> B
   * We have an entity `A` that has a child `B` and we move this child from `A` to `C`
   *
   * Produced events: [Replace(A), Replace(B), Replace(C)]
   */
  public val eventLog: Flow<VersionedStorageChange>

  /**
   * Returns the actual instance of VirtualFileUrlManager for URLs (in the Virtual File System format) of files that
   * are referenced from workspace model entities.
   */
  public fun getVirtualFileUrlManager(): VirtualFileUrlManager

  public companion object {
    /**
     * In suspend context use [serviceAsync]
     */
    @JvmStatic
    @RequiresBlockingContext
    public fun getInstance(project: Project): WorkspaceModel = project.service()
  }
}

/**
 * A shorter variant of [WorkspaceModel.getInstance].
 */
public val Project.workspaceModel: WorkspaceModel
  get() = WorkspaceModel.getInstance(this)
