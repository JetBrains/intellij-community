// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.List
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class UnknownSdkEditorNotification(val project: Project, val scope: CoroutineScope) {
  private val notifications = AtomicReference<MutableList<UnknownSdkFix>>(mutableListOf<UnknownSdkFix>())

  fun allowProjectSdkNotifications(): Boolean {
    return notifications.get().isEmpty()
  }

  fun getNotifications(): MutableList<UnknownSdkFix> {
    return notifications.get()
  }

  fun showNotifications(notifications: MutableList<out UnknownSdkFix>) {
    var notifications = notifications
    if (!notifications.isEmpty() && !`is`("unknown.sdk.show.editor.actions")) {
      notifications = mutableListOf<UnknownSdkFix>()
    }
    this.notifications.set(List.copyOf<UnknownSdkFix>(notifications))
    EditorNotifications.getInstance(project).updateAllNotifications()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): UnknownSdkEditorNotification {
      return project.getService(UnknownSdkEditorNotification::class.java)
    }
  }
}

internal class UnknownSdkEditorNotificationsProvider : EditorNotificationProvider {
  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<in FileEditor, out JComponent?>? {
    return Function { editor: FileEditor ->
      val sdkService: UnknownSdkEditorNotification = UnknownSdkEditorNotification.getInstance(project)
      val panels: MutableList<EditorNotificationPanel?> = ArrayList()
      for (info in sdkService.getNotifications()) {
        if (info.isRelevantFor(project, file)) {
          panels.add(UnknownSdkEditorPanel(project, editor, info))
        }
      }

      when {
        panels.isEmpty() -> null
        panels.size == 1 -> panels[0]
        else -> {
          val panel = JPanel(VerticalFlowLayout(0, 0))
          EditorNotificationPanel.wrapPanels(panels, panel, EditorNotificationPanel.Status.Warning)
          panel
        }
      }
    }
  }
}