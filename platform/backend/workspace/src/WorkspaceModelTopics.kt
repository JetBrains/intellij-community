// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Obsolete listener for the workspace model. Use [WorkspaceModel.eventLog]
 */
@ApiStatus.Obsolete
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
@ApiStatus.Internal
public interface WorkspaceModelUnloadedStorageChangeListener : EventListener {
  /**
   * This method is invoked under Write Action after changes are applied.
   * If its implementation involves heavy computations, it's better to schedule its execution on a separate thread to avoid blocking Event Dispatch Thread.
   */
  public fun changed(event: VersionedStorageChange)
}

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
public class WorkspaceModelTopics {
  public companion object {
    /**
     * Obsolete topic for the workspace model. Use [WorkspaceModel.eventLog]
     */
    @Topic.ProjectLevel
    @JvmField
    @ApiStatus.Obsolete
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

    @ApiStatus.ScheduledForRemoval
    @Deprecated("This service should not be used")
    public fun getInstance(project: Project): WorkspaceModelTopics = project.service()
  }

  @Deprecated("This flag should not be used")
  public var modulesAreLoaded: Boolean = false
    private set

  @Deprecated("This flag should not be used")
  public fun notifyModulesAreLoaded() {
    modulesAreLoaded = true
  }
}
