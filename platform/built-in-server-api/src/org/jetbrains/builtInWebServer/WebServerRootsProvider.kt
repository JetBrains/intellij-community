// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

abstract class WebServerRootsProvider {
  @ApiStatus.Internal
  companion object {
    val EP_NAME: ExtensionPointName<WebServerRootsProvider> = ExtensionPointName("org.jetbrains.webServerRootsProvider")
  }

  abstract fun resolve(path: String, project: Project, pathQuery: PathQuery): PathInfo?

  abstract fun getPathInfo(file: VirtualFile, project: Project): PathInfo?

  open fun isClearCacheOnFileContentChanged(file: VirtualFile): Boolean = false
}

class PathQuery @ApiStatus.Internal constructor(
  val searchInLibs: Boolean,
  val searchInArtifacts: Boolean,
  val useHtaccess: Boolean,
  val useVfs: Boolean,
) {
  @ApiStatus.Internal
  constructor() : this(searchInLibs = true, searchInArtifacts = true, useHtaccess = true, useVfs = false)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PathQuery) return false

    return searchInLibs == other.searchInLibs &&
           searchInArtifacts == other.searchInArtifacts &&
           useHtaccess == other.useHtaccess &&
           useVfs == other.useVfs
  }

  override fun hashCode(): Int {
    var result = searchInLibs.hashCode()
    result = 31 * result + searchInArtifacts.hashCode()
    result = 31 * result + useHtaccess.hashCode()
    result = 31 * result + useVfs.hashCode()
    return result
  }

  override fun toString(): String {
    return "PathQuery(searchInLibs=$searchInLibs, searchInArtifacts=$searchInArtifacts, useHtaccess=$useHtaccess, useVfs=$useVfs)"
  }
}
