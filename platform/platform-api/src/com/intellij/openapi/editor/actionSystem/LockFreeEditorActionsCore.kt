// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actionSystem

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.text.HtmlBuilder
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration.Companion.minutes

@ApiStatus.Internal
object LockFreeEditorActionsCore {
  var overrideNeedLockForArrowActions: Boolean = false

  @Volatile
  private var lastNotificationTime: Long = 0

  @Suppress("HardCodedStringLiteral", "OPT_IN_USAGE")
  fun showBalloonWithAdvice(e: Throwable) {
    if (System.currentTimeMillis() - lastNotificationTime < 1.minutes.inWholeMilliseconds) {
      return
    } else {
      lastNotificationTime = System.currentTimeMillis()
    }
    val issueLink = "https://youtrack.jetbrains.com/issue/IJPL-231208"
    val assigneeLink = "https://jetbrains.slack.com/team/UL4EL747Q"
    val builder = HtmlBuilder()
      .append("An IDE operation failed because of recent changes in read access (")
      .appendLink(issueLink, "see IJPL-231208")
      .append(")");
    if (ApplicationManager.getApplication().isInternal) {
      builder.append(" Please report it to ")
        .appendLink(assigneeLink, "Konstantin Nisht")
        .append(".")
    }
    val notification = Notification("IDE-errors",
                                    builder.toString(),
                                    NotificationType.WARNING)
      .addAction(NotificationAction.createSimple("Copy exception to clipboard") {
        CopyPasteManager.getInstance().setContents(StringSelection(e.stackTraceToString()))
      })
      .addAction(NotificationAction.createSimpleExpiring("Fix read access errors for five minutes") {
        val currentValue = overrideNeedLockForArrowActions
        overrideNeedLockForArrowActions = true
        GlobalScope.launch {
          delay(5.minutes)
          overrideNeedLockForArrowActions = currentValue
        }
      })
      .addAction(NotificationAction.createSimpleExpiring("Fix read access errors until restart") {
        overrideNeedLockForArrowActions = true
      })
    notification.setListener { _, event ->
      val linkString = event.url.toString()
      if (linkString == issueLink || linkString == assigneeLink) {
        BrowserUtil.browse(event.url)
      }
    }
    notification.notify(null)
    GlobalScope.launch {
      delay(1.minutes)
      notification.expire()
    }
  }

}
