// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class ProjectViewUpdateCause : Comparable<ProjectViewUpdateCause> {
  companion object {
    fun plugin(pluginId: String): ProjectViewUpdateCause = ProjectView3rdPartyPluginUpdateCause(pluginId)

    @JvmField val UNKNOWN: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("UNKNOWN")
    @JvmField val LEGACY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("LEGACY")
    @JvmField val SETTINGS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("SETTINGS")
    @JvmField val ACTION: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("ACTION")
    @JvmField val BOOKMARKS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("BOOKMARKS")
    @JvmField val CLIPBOARD: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("CLIPBOARD")
    @JvmField val EXTENSIONS_CHANGED: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("EXTENSIONS_CHANGED")
    @JvmField val PSI_FLATTEN_PACKAGES: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PSI_FLATTEN_PACKAGES")
    @JvmField val PSI_SCRATCH: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PSI_SCRATCH")
    @JvmField val PSI_PROPERTY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PSI_PROPERTY")
    @JvmField val PSI_CHILDREN: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PSI_CHILDREN")
    @JvmField val PSI_MOVE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PSI_MOVE")
    @JvmField val ROOTS_LIBRARY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("ROOTS_LIBRARY")
    @JvmField val ROOTS_MODULE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("ROOTS_MODULE")
    @JvmField val ROOTS_EP: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("ROOTS_EP")
    @JvmField val FILE_OPENED: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("FILE_OPENED")
    @JvmField val FILE_CLOSED: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("FILE_CLOSED")
    @JvmField val FILE_STATUS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("FILE_STATUS")
    @JvmField val FILE_STATUSES: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("FILE_STATUSES")
    @JvmField val FILE_APPEARANCE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("FILE_APPEARANCE")
    @JvmField val VFS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("VFS")
    @JvmField val VFS_CREATE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("VFS_CREATE")
    @JvmField val VFS_COPY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("VFS_COPY")
    @JvmField val VFS_MOVE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("VFS_MOVE")
    @JvmField val VFS_DELETE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("VFS_DELETE")
    @JvmField val PROBLEMS_APPEARED: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PROBLEMS_APPEARED")
    @JvmField val PROBLEMS_DISAPPEARED: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PROBLEMS_DISAPPEARED")
    @JvmField val SCOPE_CHOOSER: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("SCOPE_CHOOSER")
    @JvmField val SCRATCHES: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("SCRATCHES")
    @JvmField val REFACTORING: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("REFACTORING")
    @JvmField val ANDROID: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("ANDROID")
    @JvmField val PLUGIN_BAZEL: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_BAZEL")
    @JvmField val PLUGIN_COVERAGE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_COVERAGE")
    @JvmField val PLUGIN_DART: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_DART")
    @JvmField val PLUGIN_DBE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_DBE")
    @JvmField val PLUGIN_DTS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_DTS")
    @JvmField val PLUGIN_JAVAEE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_JAVAEE")
    @JvmField val PLUGIN_JUPYTER: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_JUPYTER")
    @JvmField val PLUGIN_PROJECT_FRAGMENTS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_PROJECT_FRAGMENTS")
    @JvmField val PLUGIN_PHP: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_PHP")
    @JvmField val PLUGIN_PROPERTIES: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_PROPERTIES")
    @JvmField val PLUGIN_PUPPET: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_PUPPET")
    @JvmField val PLUGIN_PYTHON: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_PYTHON")
    @JvmField val PLUGIN_REACT_BUDDY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_REACT_BUDDY")
    @JvmField val PLUGIN_RUBY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_RUBY")
    @JvmField val PLUGIN_SPRING: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_SPRING")
    @JvmField val PLUGIN_WORKSPACE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_WORKSPACE")
    @JvmField val PLUGIN_XPATH: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("PLUGIN_XPATH")
    @JvmField val DEBUG_VFS_INFO: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("DEBUG_VFS_INFO")
    @JvmField val DEBUG_INDEXABILITY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause("DEBUG_INDEXABILITY")
  }

  override fun compareTo(other: ProjectViewUpdateCause): Int {
    return when (this) {
      is ProjectViewStandardUpdateCause -> when (other) {
        is ProjectViewStandardUpdateCause -> id.compareTo(other.id)
        is ProjectView3rdPartyPluginUpdateCause -> 1
      }
      is ProjectView3rdPartyPluginUpdateCause -> when (other) {
        is ProjectView3rdPartyPluginUpdateCause -> pluginId.compareTo(other.pluginId)
        is ProjectViewStandardUpdateCause -> -1
      }
    }
  }
}

private data class ProjectViewStandardUpdateCause(
  val id: String
): ProjectViewUpdateCause() {
  override fun toString(): String = id
}

private data class ProjectView3rdPartyPluginUpdateCause(
  val pluginId: String
): ProjectViewUpdateCause() {
  override fun toString(): String = "PLUGIN=$pluginId"
}
