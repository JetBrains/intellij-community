// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util

import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.util.DiffNotificationProvider
import com.intellij.diff.util.SyncHeightComponent
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.diff.DiffBundle
import com.intellij.ui.EditorNotificationPanel
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.JPanel

object DiffNotifications {
  private val DEFAULT_NOTIFICATION_STATUS: EditorNotificationPanel.Status = EditorNotificationPanel.Status.Warning

  @JvmStatic
  fun createInsertedContent(): JPanel {
    return createNotification(DiffBundle.message("notification.status.content.added"), TextDiffType.INSERTED.getColor(null), status = EditorNotificationPanel.Status.Info)
  }

  @JvmStatic
  fun createRemovedContent(): JPanel {
    return createNotification(DiffBundle.message("notification.status.content.removed"), TextDiffType.DELETED.getColor(null), status = EditorNotificationPanel.Status.Info)
  }

  @JvmStatic
  @JvmOverloads
  fun createEqualContents(equalCharsets: Boolean = true, equalSeparators: Boolean = true): JPanel {
    val message = when {
      !equalCharsets && !equalSeparators -> DiffBundle.message("diff.contents.have.differences.only.in.charset.and.line.separators.message.text")
      !equalSeparators -> DiffBundle.message("diff.contents.have.differences.only.in.line.separators.message.text")
      !equalCharsets -> DiffBundle.message("diff.contents.have.differences.only.in.charset.message.text")
      else -> DiffBundle.message("diff.contents.are.identical.message.text")
    }
    return createNotification(message)
  }

  @JvmStatic
  fun createError(): JPanel {
    return createNotification(DiffBundle.message("diff.cant.calculate.diff"))
  }

  fun createOperationCanceled(): JPanel {
    return createNotification(DiffBundle.message("error.can.not.calculate.diff.operation.canceled"))
  }

  @JvmStatic
  fun createDiffTooBig(): JPanel {
    return createNotification(DiffBundle.message("error.can.not.calculate.diff.file.too.big"))
  }

  //
  // Impl
  //
  @JvmStatic
  @JvmOverloads
  fun createNotificationProvider(text: @Nls String,
                                 background: Color? = null,
                                 status: EditorNotificationPanel.Status = DEFAULT_NOTIFICATION_STATUS): DiffNotificationProvider {
    return DiffNotificationProvider { _: DiffViewer? -> createNotification(text, background, status = status) }
  }

  @JvmStatic
  @JvmOverloads
  fun createNotification(text: @Nls String,
                         background: Color? = null,
                         status: EditorNotificationPanel.Status = DEFAULT_NOTIFICATION_STATUS,
                         showHideAction: Boolean = true): JPanel {
    val panel = EditorNotificationPanel(background, status)
    panel.text(text)
    if (showHideAction) {
      val link = panel.createActionLabel(DiffBundle.message("button.hide.notification")) { hideNotification(panel) }
      link.toolTipText = DiffBundle.message("hide.this.notification")
    }
    return panel
  }

  @JvmStatic
  fun hideNotification(panel: EditorNotificationPanel) {
    panel.isVisible = false
    val syncComponent = UIUtil.getParentOfType(SyncHeightComponent::class.java, panel)
    syncComponent?.revalidateAll()
  }
}