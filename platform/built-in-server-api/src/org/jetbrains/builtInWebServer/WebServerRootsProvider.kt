// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

data class PathQuery(val searchInLibs: Boolean = true, val searchInArtifacts: Boolean = true, val useHtaccess: Boolean = true, val useVfs: Boolean = false)

abstract class WebServerRootsProvider {
  companion object {
    val EP_NAME: ExtensionPointName<WebServerRootsProvider> = ExtensionPointName("org.jetbrains.webServerRootsProvider")
  }
  
  abstract fun resolve(path: String, project: Project, pathQuery: PathQuery): PathInfo?

  abstract fun getPathInfo(file: VirtualFile, project: Project): PathInfo?

  open fun isClearCacheOnFileContentChanged(file: VirtualFile): Boolean = false
}