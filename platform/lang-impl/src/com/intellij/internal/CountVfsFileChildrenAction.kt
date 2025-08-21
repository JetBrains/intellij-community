// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

internal class CountVfsFileChildrenAction : AnAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val virtualFile = VIRTUAL_FILE.getData(e.dataContext)
    e.presentation.setEnabledAndVisible(virtualFile != null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val root = VIRTUAL_FILE.getData(e.dataContext) ?: return
    val project = e.project ?: return

    currentThreadCoroutineScope().launch(Dispatchers.Default) {
      withBackgroundProgress(project, "Counting children on disk recursively...") {
        reportRawProgress { reporter ->
          var filesOnDiskCount = 0
          VfsUtilCore.visitChildrenRecursively((root as NewVirtualFile).asCacheAvoiding(), object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
              filesOnDiskCount++
              if (filesOnDiskCount % 100 == 0) {
                reporter.text("Counting children on disk recursively ($filesOnDiskCount)")
              }
              return true
            }
          })
          val message = "Under <i>${root.path}</i><br/>" +
                        "there are $filesOnDiskCount files on <b>disk</b>"
          Notification("System Messages", message, NotificationType.INFORMATION)
            .notify(project)
        }
      }
    }

    currentThreadCoroutineScope().launch(Dispatchers.Default) {
      val fileIndex = ProjectFileIndex.getInstance(project)
      withBackgroundProgress(project, "Counting children in VFS recursively...") {
        var vfsFilesCount = 0
        var contentFilesCount = 0
        var excludedFilesCount = 0
        var ignoredFilesCount = 0
        visitChildrenInVfsRecursively(root) { file ->
          vfsFilesCount++
          runReadAction {
            when {
              fileIndex.isInContent(file) -> contentFilesCount++
              fileIndex.isUnderIgnored(file) -> ignoredFilesCount++
              fileIndex.isExcluded(file) -> excludedFilesCount++
            }
          }
          true
        }
        val message = "Under <i>${root.path}</i><br/>" +
                      "there are $vfsFilesCount files in <b>VFS</b>:<br/>" +
                      "$contentFilesCount content files<br/>" +
                      "$excludedFilesCount excluded files<br/>" +
                      "$ignoredFilesCount ignored files"
        Notification("System Messages", message, NotificationType.INFORMATION)
          .notify(project)
      }
    }
  }
}

@ApiStatus.Internal
fun visitChildrenInVfsRecursively(file: VirtualFile, action: (VirtualFile) -> Boolean) {
  VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Unit>() {
    override fun getChildrenIterable(file: VirtualFile): Iterable<VirtualFile?> {
      val id = (file as? VirtualFileWithId)?.id ?: return emptyList()
      val fs = ManagingFS.getInstance()
      return FSRecords.getInstance().list(id).children.map { fs.findFileById(it.id) }
    }

    override fun visitFile(file: VirtualFile): Boolean {
      return action(file)
    }
  })
}
