// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.vcs

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RecentProjectsBranchesProvider {
  fun getCurrentBranch(projectPath: String, nameIsDistinct: Boolean): String?

  /**
   * Whether the recent project at [projectPath] is under this version control system.
   *
   * Asked separately from [getCurrentBranch], which answers whether there is a branch name worth showing: a display setting, a detached
   * HEAD or a project on a remote file system all turn that into null for a project that is under version control all the same.
   */
  fun hasRepository(projectPath: String): Boolean
}