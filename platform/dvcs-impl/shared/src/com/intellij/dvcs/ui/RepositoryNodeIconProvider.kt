// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import javax.swing.Icon

/**
 * Replaces the icon of a repository row of a VCS tree, see [VcsRepositoryIconsProvider].
 *
 * The default icon shows the color of the repository. A frame that shows one project per repository can show
 * the icon of the project instead.
 */
@ApiStatus.Internal
interface RepositoryNodeIconProvider {
  /** The icon of the row of the repository at [rootPath] in [project], or `null` to keep the default icon. */
  fun getIcon(project: Project, rootPath: Path): Icon?

  companion object {
    private val EP_NAME: ExtensionPointName<RepositoryNodeIconProvider> = ExtensionPointName("com.intellij.vcs.repositoryNodeIconProvider")

    /** The icon of the first provider that answers for the repository at [rootPath] in [project], or `null`. */
    fun getIcon(project: Project, rootPath: Path): Icon? = EP_NAME.extensionList.firstNotNullOfOrNull { it.getIcon(project, rootPath) }
  }
}
