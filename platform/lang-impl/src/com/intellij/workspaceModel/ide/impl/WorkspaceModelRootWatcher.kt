// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.RootsChangeWatcher

class WorkspaceModelRootWatcher : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    val appliers = ProjectManager.getInstance().openProjects.flatMap {
      try {
        listOfNotNull(
          it.service<RootsChangeWatcher>().prepareChange(events),
          FileReferenceInWorkspaceEntityUpdater(it).prepareChange(events)
        )
      }
      catch (ignore: AlreadyDisposedException) {
        // ignore disposed project
        emptyList()
      }
    }

    return when {
      appliers.isEmpty() -> null
      appliers.size == 1 -> appliers.first()
      else -> {
        object : AsyncFileListener.ChangeApplier {
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
}