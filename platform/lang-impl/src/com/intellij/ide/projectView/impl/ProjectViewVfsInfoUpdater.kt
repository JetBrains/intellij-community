// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.ManagingFS
import groovy.transform.Internal
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
@Internal
internal class ProjectViewVfsInfoUpdater(val project: Project, val scope: CoroutineScope) {
  init {
    var modCount = ManagingFS.getInstance().structureModificationCount
    scope.launch(CoroutineName("VFS project view info updater")) {
      while (true) {
        delay(5.seconds)
        if (Registry.intValue("project.view.show.vfs.cached.children.count.limit") <= 0) {
          continue
        }
        val newModCount = ManagingFS.getInstance().structureModificationCount
        if (newModCount != modCount) {
          modCount = newModCount
          ProjectView.getInstance(project)?.currentProjectViewPane?.updateFromRoot(true)
        }
      }
    }
  }
}