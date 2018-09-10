// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.settings

import com.intellij.ui.ListCellRendererWrapper
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription
import javax.swing.JList

/**
 * @author nik
 */
sealed class VersionItem(val versionId: String, val displayName: String) {
  object LatestRelease : VersionItem(RepositoryLibraryDescription.ReleaseVersionId, RepositoryLibraryDescription.ReleaseVersionDisplayName)
  object LatestVersion : VersionItem(RepositoryLibraryDescription.LatestVersionId, RepositoryLibraryDescription.LatestVersionDisplayName)
  data class ExactVersion(val version: String) : VersionItem(version, version)
}

class VersionSelectorCellRenderer : ListCellRendererWrapper<VersionItem>() {
  override fun customize(list: JList<*>?, value: VersionItem?, index: Int, selected: Boolean, hasFocus: Boolean) {
    setText(value?.displayName ?: "")
  }
}