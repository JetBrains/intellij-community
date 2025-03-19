// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.NonNls
import javax.swing.JCheckBox
import javax.swing.JComponent

internal class DumpVfsInfoForFsRootAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

    val dialog = MyDialog()
    if (file != null && file.isDirectory) {
      dialog.setRoot(file.url)
    }
    if (!dialog.showAndGet()) return

    LOG.info("--- VFS CONTENT ---")
    val root = VirtualFileManager.getInstance().findFileByUrl(dialog.root)
    if (root == null) {
      val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("IDE Caches")
      notificationGroup.createNotification("VFS dump", "Root not found", NotificationType.ERROR).notify(e.project)
      return
    }

    dumpChildrenInDbRecursively(root, dialog.showFiles, dialog.depth)
    LOG.info("--- END OF VFS CONTENT ---")

    val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("IDE Caches")
    notificationGroup.createNotification("VFS dump", "VFS content is logged", NotificationType.INFORMATION).notify(e.project)
  }

  companion object {
    private val LOG = Logger.getInstance(DumpVfsInfoForFsRootAction::class.java)

    private fun dumpChildrenInDbRecursively(dir: VirtualFile, filesMode: FilesMode, depth: Int) {
      if (dir !is NewVirtualFile) {
        LOG.info("${dir.presentableUrl}: not in db (${dir.javaClass.getName()})")
        return
      }

      val dirs: MutableList<VirtualFile> = ArrayList<VirtualFile>()
      var inDb = 0
      var contentInDb = 0
      var nullChildren = 0
      val persistentFS = PersistentFS.getInstance()
      if (persistentFS.wereChildrenAccessed(dir)) {
        for (name in persistentFS.listPersisted(dir)) {
          inDb++
          val child = dir.findChild(name)
          if (child == null) {
            nullChildren++

            if (filesMode != FilesMode.NONE) {
              LOG.info("$name: invalid child")
            }
            continue
          }
          if (child.isDirectory()) {
            dirs.add(child)
          }
          else {
            val hasContentInDb = PersistentFS.getInstance().getCurrentContentId(child) != 0
            if (hasContentInDb) {
              contentInDb++
            }

            if (filesMode == FilesMode.ALL ||
                filesMode == FilesMode.LOADED_CONTENT && hasContentInDb) {
              LOG.info(listOfNotNull(
                child.presentableUrl,
                if (hasContentInDb) "has content in db" else null,
              ).joinToString(", "))
            }

          }
        }
      }

      LOG.info(listOfNotNull(
        "${dir.presentableUrl}: $inDb children in db",
        if (contentInDb > 0) "content of $contentInDb children in db" else null,
        if (nullChildren > 0) "$nullChildren invalid children in db" else null
      ).joinToString(", "))

      val skipChildren = !dirs.isEmpty() && depth == 0
      if (skipChildren) {
        LOG.info("too deep, skipping children for '${dir.name}'")
      }
      else {
        for (childDir in dirs) {
          dumpChildrenInDbRecursively(childDir, filesMode, depth - 1)
        }
      }
    }
  }

  private class MyDialog : DialogWrapper(null) {
    private lateinit var rootField: JBTextField
    private lateinit var showFilesCheckBox: JCheckBox
    private lateinit var loadedFilesOnlyCheckBox: JCheckBox
    private lateinit var depthField: JBTextField

    fun setRoot(url: String) {
      rootField.text = url
    }

    val root: String get() = rootField.text
    val depth: Int get() = depthField.text.toIntOrNull() ?: 0
    val showFiles: FilesMode
      get() = when {
        !showFilesCheckBox.isSelected -> FilesMode.NONE
        loadedFilesOnlyCheckBox.isSelected -> FilesMode.LOADED_CONTENT
        else -> FilesMode.ALL
      }

    init {
      title = "Dump VFS Info"
      init()
    }

    override fun getDimensionServiceKey(): @NonNls String = "DumpVfsInfoForFsRootAction"

    override fun createCenterPanel(): JComponent? {
      return panel {
        row("Root:") {
          rootField = textField()
            .applyToComponent {
              text = when {
                SystemInfo.isWindows -> "file://C:/"
                else -> "file:///"
              }
            }
            .resizableColumn()
            .align(AlignX.FILL)
            .apply {
              if (SystemInfo.isWindows) {
                comment("Each disk is handled independently. Use file://C:/ or file://wsl.localhost/Ubuntu/")
              }
            }
            .component
        }
        row("Depth:") {
          depthField = intTextField(null, null)
            .applyToComponent { text = "4" }
            .component
        }
        row {
          showFilesCheckBox = checkBox("Show all files")
            .applyToComponent { isSelected = false }
            .component
        }
        indent {
          row {
            loadedFilesOnlyCheckBox = checkBox("Only files with content loaded into VFS")
              .applyToComponent { isSelected = true }
              .enabledIf(showFilesCheckBox.selected)
              .component
          }
        }
      }
    }
  }

  private enum class FilesMode {
    NONE, LOADED_CONTENT, ALL
  }
}
