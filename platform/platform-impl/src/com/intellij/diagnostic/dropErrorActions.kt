// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import java.awt.event.InputEvent

private const val TEST_LOGGER = "TEST.LOGGER"
private const val TEST_MESSAGE = "test exception; please ignore"

class DropAnErrorAction : DumbAwareAction("Drop an error") {
  override fun actionPerformed(e: AnActionEvent) {
    Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception())
  }
}

class DropAnErrorWithAttachmentsAction : DumbAwareAction("Drop An Error With Attachments", "Hold down SHIFT for multiple attachments", null) {
  override fun actionPerformed(e: AnActionEvent) {
    val attachments = if (e.modifiers and InputEvent.SHIFT_MASK != 0) {
      arrayOf(Attachment("first.txt", "content"), Attachment("second.txt", "more content"), Attachment("third.txt", "even more content"))
    }
    else {
      arrayOf(Attachment("attachment.txt", "content"))
    }
    Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception(), *attachments)
  }
}

class DropAnOutOfMemoryErrorAction : DumbAwareAction("Drop an OutOfMemoryError") {
  override fun actionPerformed(e: AnActionEvent) {
    val array = arrayOfNulls<Any>(Integer.MAX_VALUE)
    for (i in array.indices) {
      array[i] = arrayOfNulls<Any>(Integer.MAX_VALUE)
    }
    throw OutOfMemoryError()
  }
}

class DropAnOutOfMetaspaceErrorAction : DumbAwareAction("Drop an OutOfMemoryError in Metaspace") {
  override fun actionPerformed(e: AnActionEvent) {
    throw OutOfMemoryError("foo Metaspace foo")
  }
}