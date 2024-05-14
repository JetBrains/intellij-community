// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui.actions

import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.ui.views.DirectoryHistoryDialog
import com.intellij.history.integration.ui.views.FileHistoryDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lvcs.impl.ActivityScope.Companion.fromFiles
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter.logLocalHistoryOpened
import com.intellij.platform.lvcs.impl.ui.ActivityView.Companion.isViewEnabled
import com.intellij.platform.lvcs.impl.ui.ActivityView.Companion.showInDialog
import com.intellij.util.containers.ContainerUtil

internal fun isFileVersioned(gw: IdeaGateway, file: VirtualFile): Boolean {
  return gw.isVersioned(file) && (file.isDirectory || gw.areContentChangesVersioned(file))
}

internal fun canShowLocalHistoryFor(gw: IdeaGateway, files: Collection<VirtualFile>): Boolean {
  if (files.size > 1 && !isViewEnabled()) return false
  return files.any { file -> isFileVersioned(gw, file) }
}

internal fun showLocalHistoryFor(project: Project, gateway: IdeaGateway, selectedFiles: Collection<VirtualFile>) {
  val enabledFiles = selectedFiles.filter { file -> isFileVersioned(gateway, file) }
  if (enabledFiles.isEmpty()) return

  if (isViewEnabled()) {
    showInDialog(project, gateway, fromFiles(enabledFiles))
    return
  }

  val singleFile = ContainerUtil.getOnlyItem(enabledFiles)
  if (singleFile == null) return

  if (singleFile.isDirectory) {
    logLocalHistoryOpened(LocalHistoryCounter.Kind.Directory)
    DirectoryHistoryDialog(project, gateway, singleFile).show()
  }
  else {
    logLocalHistoryOpened(LocalHistoryCounter.Kind.File)
    FileHistoryDialog(project, gateway, singleFile).show()
  }
}