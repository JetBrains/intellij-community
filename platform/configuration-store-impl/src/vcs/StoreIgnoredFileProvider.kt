package com.intellij.configurationStore.vcs

import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.IgnoredFileProvider

private class StoreIgnoredFileProvider : IgnoredFileProvider {
  override fun isIgnoredFile(project: Project, filePath: FilePath): Boolean {
    val store = project.stateStore as IProjectStore
    return filePath.path.equals(store.workspaceFilePath, !SystemInfo.isFileSystemCaseSensitive)
  }
}