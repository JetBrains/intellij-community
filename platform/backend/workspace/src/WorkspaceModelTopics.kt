// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.messages.Topic
import java.util.*

/**
 * Register an implementation of this interface as a handler for [WorkspaceModelTopics.CHANGED] to synchronously process changes in the 
 * workspace model.
 * 
 * For the asynchronous handling of changes from the workspace model collect them via [WorkspaceModel.changesEventFlow]
 */
interface WorkspaceModelChangeListener : EventListener {
  /**
   * This method is invoked under Write Action before changes are applied.
   * Please note that [event] contains information about old and new versions of the changed entities, and it's recommended to override it 
   * instead. 
   */
  fun beforeChanged(event: VersionedStorageChange) {}

  /**
   * This method is invoked under Write Action after changes are applied. 
   * If its implementation involves heavy computations, it's better to schedule its execution on a separate thread to avoid blocking Event Dispatch Thread.
   */
  fun changed(event: VersionedStorageChange) {}
}

/**
 * Topics to subscribe to Workspace changes.
 *
 * For the asynchronous approach please consider to collect changes from [WorkspaceModel.changesEventFlow]
 */
@Service(Service.Level.PROJECT)
class WorkspaceModelTopics : Disposable {
  companion object {
    @Topic.ProjectLevel
    @JvmField
    val CHANGED: Topic<WorkspaceModelChangeListener> = Topic(WorkspaceModelChangeListener::class.java, Topic.BroadcastDirection.NONE, true)

    /**
     * Subscribe to this topic to be notified about changes in unloaded entities. 
     * Note that most of the code should simply ignore unloaded entities and therefore shouldn't be interested in these changes. 
     */
    @Topic.ProjectLevel
    @JvmField
    val UNLOADED_ENTITIES_CHANGED: Topic<WorkspaceModelChangeListener> = Topic(WorkspaceModelChangeListener::class.java,
                                                                               Topic.BroadcastDirection.NONE, true)

    fun getInstance(project: Project): WorkspaceModelTopics = project.service()
  }

  var modulesAreLoaded: Boolean = false
    private set

  fun notifyModulesAreLoaded() {
    modulesAreLoaded = true
  }

  override fun dispose() {
  }
}
