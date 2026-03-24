// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.io.jackson.array
import tools.jackson.core.JsonGenerator

internal class OpenedProjectRootsContributor : BuiltInServerInfoContributor, ProjectActivity {
  override fun contribute(generator: JsonGenerator) {
    val paths = ProjectManager.getInstance().openProjects
      .filter { !it.isDisposed }
      .mapNotNull { it.basePath }
    if (paths.isEmpty()) return

    generator.array("openProjectPaths") {
      for (path in paths) {
        generator.writeString(path)
      }
    }
  }

  override suspend fun execute(project: Project) {
    serviceAsync<BuiltInServerDiscoveryService>().notifyUpdate()
  }
}

internal class ProjectRootsProjectCloseListener : ProjectCloseListener {
  override fun projectClosed(project: Project) {
    BuiltInServerDiscoveryService.getInstance().scheduleNotifyUpdate()
  }
}
