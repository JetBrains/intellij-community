// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AfterEventShouldBeFiredBeforeOtherListeners
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.RootsChangeWatcher
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WorkspaceModelRootWatcher : AsyncFileListener, AfterEventShouldBeFiredBeforeOtherListeners {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    val appliers = ProjectManager.getInstance().openProjects.flatMap { project ->
      try {
        listOfNotNull(
          RootsChangeWatcher.prepareChange(events, project),
          FileReferenceInWorkspaceEntityUpdater(project).prepareChange(events)
        )
      }
      catch (ignore: AlreadyDisposedException) {
        // ignore disposed project
        emptyList()
      }
    }

    return if (appliers.isEmpty()) null
    else {
        object : AsyncFileListener.ChangeApplier, AfterEventShouldBeFiredBeforeOtherListeners {
          override fun beforeVfsChange() {
            for (applier in appliers) {
              applier.beforeVfsChange()
            }
          }

          override fun afterVfsChange() {
            for (applier in appliers) {
              applier.afterVfsChange()
            }
          }
        }
    }
  }
}