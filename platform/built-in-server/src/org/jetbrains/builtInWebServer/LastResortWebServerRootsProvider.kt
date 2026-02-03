// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class LastResortWebServerRootsProvider : WebServerRootsProvider() {
  override fun resolve(path: String, project: Project, pathQuery: PathQuery): PathInfo? {
    for (baseDirectory in project.getBaseDirectories()) {
      val file = baseDirectory.findFileByRelativePath(path) ?: continue
      return PathInfo(ioFile = null, file, baseDirectory)
    }
    return null
  }

  override fun getPathInfo(file: VirtualFile, project: Project): PathInfo? {
    val root = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(file)
    return if (root != null) PathInfo(null, file, root) else null
  }
}
