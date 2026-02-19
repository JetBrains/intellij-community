// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

/**
 * Specifies the cause why the Project View is going to be updated
 *
 * The cause is used to monitor Project View performance and is passed to functions that cause Project View updates.
 *
 * A cause has an id ([ProjectViewUpdateCauseId]) which determines the type of the cause.
 *
 * There are standard types available as companion object members.
 * For new use cases, new enum values can be added and registered as new members here.
 * **Don't forget to increment the group version in `com.intellij.ide.projectView.impl.ProjectViewPerformanceCollector` (`"project.view.performance"`).**
 *
 * For 3rd party plugins, the ID will be [ProjectViewUpdateCauseId.PLUGIN_3RD_PARTY] and the necessary information about the plugin
 * will be detected automatically from the call stack. This means that function overloads that don't accept a cause should not be called
 * internally because the plugin detection will fail and will be logged. For example, to refresh the Project View from monorepo code,
 * call `refresh(ProjectViewUpdateCause)` on the Project View and not just `refresh()`.
 */
@ApiStatus.Internal
sealed class ProjectViewUpdateCause : Comparable<ProjectViewUpdateCause> {
  companion object {
    fun plugin(pluginId: PluginId): ProjectViewUpdateCause = ProjectView3rdPartyPluginUpdateCause(pluginId)

    @JvmField val UNKNOWN: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.UNKNOWN)
    @JvmField val LEGACY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.LEGACY)
    @JvmField val SETTINGS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.SETTINGS)
    @JvmField val ACTION: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.ACTION)
    @JvmField val BOOKMARKS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.BOOKMARKS)
    @JvmField val CLIPBOARD: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.CLIPBOARD)
    @JvmField val EXTENSIONS_CHANGED: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.EXTENSIONS_CHANGED)
    @JvmField val PSI_FLATTEN_PACKAGES: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PSI_FLATTEN_PACKAGES)
    @JvmField val PSI_SCRATCH: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PSI_SCRATCH)
    @JvmField val PSI_PROPERTY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PSI_PROPERTY)
    @JvmField val PSI_CHILDREN: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PSI_CHILDREN)
    @JvmField val PSI_MOVE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PSI_MOVE)
    @JvmField val ROOTS_LIBRARY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.ROOTS_LIBRARY)
    @JvmField val ROOTS_MODULE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.ROOTS_MODULE)
    @JvmField val ROOTS_EP: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.ROOTS_EP)
    @JvmField val FILE_OPENED: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.FILE_OPENED)
    @JvmField val FILE_CLOSED: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.FILE_CLOSED)
    @JvmField val FILE_STATUS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.FILE_STATUS)
    @JvmField val FILE_STATUSES: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.FILE_STATUSES)
    @JvmField val FILE_APPEARANCE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.FILE_APPEARANCE)
    @JvmField val VFS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.VFS)
    @JvmField val VFS_CREATE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.VFS_CREATE)
    @JvmField val VFS_COPY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.VFS_COPY)
    @JvmField val VFS_MOVE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.VFS_MOVE)
    @JvmField val VFS_DELETE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.VFS_DELETE)
    @JvmField val PROBLEMS_APPEARED: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PROBLEMS_APPEARED)
    @JvmField val PROBLEMS_DISAPPEARED: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PROBLEMS_DISAPPEARED)
    @JvmField val SCOPE_CHOOSER: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.SCOPE_CHOOSER)
    @JvmField val SCRATCHES: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.SCRATCHES)
    @JvmField val REFACTORING: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.REFACTORING)
    @JvmField val ANDROID: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.ANDROID)
    @JvmField val PLUGIN_BAZEL: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_BAZEL)
    @JvmField val PLUGIN_COVERAGE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_COVERAGE)
    @JvmField val PLUGIN_DART: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_DART)
    @JvmField val PLUGIN_DBE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_DBE)
    @JvmField val PLUGIN_DTS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_DTS)
    @JvmField val PLUGIN_JAVAEE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_JAVAEE)
    @JvmField val PLUGIN_JUPYTER: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_JUPYTER)
    @JvmField val PLUGIN_PROJECT_FRAGMENTS: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_PROJECT_FRAGMENTS)
    @JvmField val PLUGIN_PHP: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_PHP)
    @JvmField val PLUGIN_PROPERTIES: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_PROPERTIES)
    @JvmField val PLUGIN_PUPPET: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_PUPPET)
    @JvmField val PLUGIN_PYTHON: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_PYTHON)
    @JvmField val PLUGIN_REACT_BUDDY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_REACT_BUDDY)
    @JvmField val PLUGIN_RUBY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_RUBY)
    @JvmField val PLUGIN_SPRING: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_SPRING)
    @JvmField val PLUGIN_WORKSPACE: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_WORKSPACE)
    @JvmField val PLUGIN_XPATH: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.PLUGIN_XPATH)
    @JvmField val DEBUG_VFS_INFO: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.DEBUG_VFS_INFO)
    @JvmField val DEBUG_INDEXABILITY: ProjectViewUpdateCause = ProjectViewStandardUpdateCause(ProjectViewUpdateCauseId.DEBUG_INDEXABILITY)
  }

  val id: ProjectViewUpdateCauseId
    get() = when (this) {
      is ProjectView3rdPartyPluginUpdateCause -> ProjectViewUpdateCauseId.PLUGIN_3RD_PARTY
      is ProjectViewStandardUpdateCause -> causeId
    }

  override fun compareTo(other: ProjectViewUpdateCause): Int {
    return when (this) {
      is ProjectViewStandardUpdateCause -> when (other) {
        is ProjectViewStandardUpdateCause -> causeId.compareTo(other.causeId)
        is ProjectView3rdPartyPluginUpdateCause -> 1
      }
      is ProjectView3rdPartyPluginUpdateCause -> when (other) {
        is ProjectView3rdPartyPluginUpdateCause -> pluginId.compareTo(other.pluginId)
        is ProjectViewStandardUpdateCause -> -1
      }
    }
  }
}

@ApiStatus.Internal
enum class ProjectViewUpdateCauseId {
  PLUGIN_3RD_PARTY,

  UNKNOWN,
  LEGACY,
  SETTINGS,
  ACTION,
  BOOKMARKS,
  CLIPBOARD,
  EXTENSIONS_CHANGED,
  PSI_FLATTEN_PACKAGES,
  PSI_SCRATCH,
  PSI_PROPERTY,
  PSI_CHILDREN,
  PSI_MOVE,
  ROOTS_LIBRARY,
  ROOTS_MODULE,
  ROOTS_EP,
  FILE_OPENED,
  FILE_CLOSED,
  FILE_STATUS,
  FILE_STATUSES,
  FILE_APPEARANCE,
  VFS,
  VFS_CREATE,
  VFS_COPY,
  VFS_MOVE,
  VFS_DELETE,
  PROBLEMS_APPEARED,
  PROBLEMS_DISAPPEARED,
  SCOPE_CHOOSER,
  SCRATCHES,
  REFACTORING,
  ANDROID,
  PLUGIN_BAZEL,
  PLUGIN_COVERAGE,
  PLUGIN_DART,
  PLUGIN_DBE,
  PLUGIN_DTS,
  PLUGIN_JAVAEE,
  PLUGIN_JUPYTER,
  PLUGIN_PROJECT_FRAGMENTS,
  PLUGIN_PHP,
  PLUGIN_PROPERTIES,
  PLUGIN_PUPPET,
  PLUGIN_PYTHON,
  PLUGIN_REACT_BUDDY,
  PLUGIN_RUBY,
  PLUGIN_SPRING,
  PLUGIN_WORKSPACE,
  PLUGIN_XPATH,
  DEBUG_VFS_INFO,
  DEBUG_INDEXABILITY,
}

@ApiStatus.Internal
data class ProjectViewStandardUpdateCause(
  val causeId: ProjectViewUpdateCauseId
): ProjectViewUpdateCause() {
  init {
    require(causeId != ProjectViewUpdateCauseId.PLUGIN_3RD_PARTY)
  }
  override fun toString(): String = causeId.toString()
}

@ApiStatus.Internal
data class ProjectView3rdPartyPluginUpdateCause(
  val pluginId: PluginId
): ProjectViewUpdateCause() {
  override fun toString(): String = "PLUGIN=$pluginId"
}
