package org.jetbrains.builtInWebServer

import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

class LastResortWebServerRootsProvider : WebServerRootsProvider() {
  override fun resolve(path: String, project: Project, pathQuery: PathQuery): PathInfo? {
    for (baseDirectory in project.getBaseDirectories()) {
      val file = VfsUtil.findRelativeFile(path, baseDirectory) ?: continue
      return PathInfo(null, file, baseDirectory)
    }
    return null
  }

  override fun getPathInfo(file: VirtualFile, project: Project): PathInfo? {
    val root = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(file)
    if (root != null) {
      return PathInfo(null, file, root)
    }
    return null
  }
}