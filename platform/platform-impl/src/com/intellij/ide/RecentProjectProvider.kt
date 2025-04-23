// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * See [com.intellij.ide.RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC]
 */
@ApiStatus.Internal
interface RecentProjectProvider {
  val providerId: @NonNls String

  fun getRecentProjects(): List<RecentProject>
}

@ApiStatus.Internal
interface RecentProject {
  val displayName: @NlsSafe String
  val projectPath: @NlsSafe String?

  /**
   * Checked out vcs branch
   */
  val branchName: @NlsSafe String?

  /**
   * "SSH", "Spaceport", etc
   */
  val providerName: @NlsSafe String?

  /**
   * "ec2-54-90.hostname.com"
   */
  val providerPath: @NlsSafe String?

  /**
   * Persistent ID associated with the project
   */
  val projectId: @NonNls String?

  /**
   * Last project access time
   */
  val activationTimestamp: Long?

  val icon: Icon?

  val providerIcon: Icon?

  fun openProject()
  fun removeFromRecent()
}