// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.messages.Topic
import java.util.*

/**
 * Register an implementation of this interface as a handler for [WorkspaceModelTopics.CHANGED] to synchronously process changes in the 
 * workspace model.
 * 
 * For the asynchronous handling of changes from the workspace model collect them via [WorkspaceModel.changesEventFlow]
 *
 * # Behavior details
 *
 * The [EntityChange.Added] and [EntityChange.Removed] events are straightforward and generated in case of added or removed entities.
 *
 * The [EntityChange.Replaced] is generated in case if any of the fields of the entity changes the value in the newer
 *   version of storage.
 * This means that this event is generated in two cases: "primitive" field change (Int, String, data class, etc.) or
 *   changes of the references to other entities. The change of references may happen interectly by modifying the referred entity.
 *   For example, if we remove child entity, we'll generate two events: remove for child and replace for parent.
 *                if we add a new child entity, we'll also generate two events: add for child and replace for parent.
 *
 * # Examples
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
public interface WorkspaceModelChangeListener : EventListener {
  /**
   * This method is invoked under Write Action before changes are applied.
   * Please note that [event] contains information about old and new versions of the changed entities, and it's recommended to override it 
   * instead. 
   */
  public fun beforeChanged(event: VersionedStorageChange) {}

  /**
   * This method is invoked under Write Action after changes are applied. 
   * If its implementation involves heavy computations, it's better to schedule its execution on a separate thread to avoid blocking Event Dispatch Thread.
   */
  public fun changed(event: VersionedStorageChange) {}
}

/**
 * Register an implementation of this interface as a handler for [WorkspaceModelTopics.UNLOADED_ENTITIES_CHANGED] to synchronously process
 * changes in the unloaded storage of the workspace model.
 *
 * See documentation in [WorkspaceModelChangeListener] to understand how events are constructed
 */
public interface WorkspaceModelUnloadedStorageChangeListener : EventListener {
  /**
   * This method is invoked under Write Action after changes are applied.
   * If its implementation involves heavy computations, it's better to schedule its execution on a separate thread to avoid blocking Event Dispatch Thread.
   */
  public fun changed(event: VersionedStorageChange)
}

/**
 * Topics to subscribe to Workspace changes.
 *
 * For the asynchronous approach please consider to collect changes from [WorkspaceModel.changesEventFlow]
 */
@Service(Service.Level.PROJECT)
public class WorkspaceModelTopics : Disposable {
  public companion object {
    @Topic.ProjectLevel
    @JvmField
    public val CHANGED: Topic<WorkspaceModelChangeListener> = Topic(WorkspaceModelChangeListener::class.java, Topic.BroadcastDirection.NONE, true)

    /**
     * Subscribe to this topic to be notified about changes in unloaded entities. 
     * Note that most of the code should simply ignore unloaded entities and therefore shouldn't be interested in these changes. 
     */
    @Topic.ProjectLevel
    @JvmField
    public val UNLOADED_ENTITIES_CHANGED: Topic<WorkspaceModelUnloadedStorageChangeListener> = Topic(
      WorkspaceModelUnloadedStorageChangeListener::class.java,
      Topic.BroadcastDirection.NONE, true
    )

    public fun getInstance(project: Project): WorkspaceModelTopics = project.service()
  }

  @Deprecated("This flag should not be used")
  public var modulesAreLoaded: Boolean = false
    private set

  @Deprecated("This flag should not be used")
  public fun notifyModulesAreLoaded() {
    modulesAreLoaded = true
  }

  override fun dispose() {
  }
}
