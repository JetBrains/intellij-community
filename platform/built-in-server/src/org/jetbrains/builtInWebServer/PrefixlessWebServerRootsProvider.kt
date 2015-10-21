package org.jetbrains.builtInWebServer

import com.intellij.openapi.project.Project

abstract class PrefixlessWebServerRootsProvider : WebServerRootsProvider() {
  override final fun resolve(path: String, project: Project) = resolve(path, project, WebServerPathToFileManager.getInstance(project).getResolver(path))

  abstract fun resolve(path: String, project: Project, resolver: FileResolver): PathInfo?
}