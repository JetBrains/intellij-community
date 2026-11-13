// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Replaces the icon of a repository node of a changes tree, see [RepositoryChangesBrowserNode].
 *
 * The default icon shows the color of the repository. A frame that shows one project per repository can show
 * the icon of the project instead.
 */
@ApiStatus.Internal
interface RepositoryNodeIconProvider {
  /** The icon of the node of [repository], or `null` to keep the default icon. */
  fun getIcon(repository: Repository): Icon?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<RepositoryNodeIconProvider> = ExtensionPointName("com.intellij.vcs.repositoryNodeIconProvider")
  }
}
