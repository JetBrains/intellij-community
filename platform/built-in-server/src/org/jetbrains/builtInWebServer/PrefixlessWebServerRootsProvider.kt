package org.jetbrains.builtInWebServer

import com.intellij.openapi.project.Project

abstract class PrefixlessWebServerRootsProvider : WebServerRootsProvider() {
  override final fun resolve(path: String, project: Project, pathQuery: PathQuery): PathInfo? = resolve(path, project, WebServerPathToFileManager.getInstance(project).getResolver(path), pathQuery)

  abstract fun resolve(path: String, project: Project, resolver: FileResolver, pathQuery: PathQuery): PathInfo?
}