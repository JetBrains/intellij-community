// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.TimeoutUtil
import java.awt.event.ActionEvent.CTRL_MASK
import java.awt.event.ActionEvent.SHIFT_MASK
import java.io.RandomAccessFile
import java.util.*

private const val TEST_LOGGER = "TEST.LOGGER"
private const val TEST_MESSAGE = "test exception; please ignore"

private val random = Random()
private fun randomString() = "random exception text ${random.nextLong()}"

@Suppress("HardCodedStringLiteral")
internal class DropAnErrorAction : DumbAwareAction("Drop an Error", "Hold down SHIFT for a sequence of exceptions", null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun actionPerformed(e: AnActionEvent) {
    if (e.modifiers and SHIFT_MASK == 0) {
      Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception(randomString()))
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread {
        for (i in 1..3) {
          Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception(randomString()))
          TimeoutUtil.sleep(200)
        }
      }
    }
  }
}

@Suppress("HardCodedStringLiteral")
internal class DropAnErrorWithAttachmentsAction : DumbAwareAction("Drop an Error with Attachments", "Hold down SHIFT for multiple attachments", null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun actionPerformed(e: AnActionEvent) {
    val attachments = if (e.modifiers and SHIFT_MASK == 0 && e.modifiers and CTRL_MASK == 0) {
      arrayOf(Attachment("attachment.txt", "content"))
    }
    else if (e.modifiers and SHIFT_MASK != 0) {
      arrayOf(Attachment("first.txt", "content"), Attachment("second.txt", "more content"), Attachment("third.txt", "even more content"))
    }
    else if (e.modifiers and CTRL_MASK != 0) {
      getLargeAttachment()
    }
    else {
      emptyArray<Attachment>()
    }
    Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception(randomString()), *attachments)
  }

  private fun getLargeAttachment(): Array<Attachment> {
    val size = 300 * 1024 * 1024
    val file = FileUtil.createTempFile("large-attachment", ".bin", true)
    RandomAccessFile(file, "rw").apply { setLength(size.toLong()) }
    return arrayOf(Attachment("large.txt", file, "A large attachment of size: $size bytes").apply { isIncluded = true })
  }
}

@Suppress("HardCodedStringLiteral")
internal class DropPluginErrorAction : DumbAwareAction("Drop an Error in a Random Plugin", "Hold down SHIFT for 3rd-party plugins only", null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun actionPerformed(e: AnActionEvent) {
    var plugins = PluginManagerCore.plugins
    if (e.modifiers and SHIFT_MASK != 0) {
      plugins = plugins.filterNot { PluginManagerCore.isDevelopedByJetBrains(it) }.toTypedArray()
    }
    if (plugins.isNotEmpty()) {
      val victim = plugins[random.nextInt(plugins.size)]
      Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, PluginException(randomString(), victim.pluginId))
    }
  }
}

@Suppress("HardCodedStringLiteral")
internal class DropAnOutOfMemoryErrorAction : DumbAwareAction("Drop an OutOfMemoryError", "Hold down SHIFT for OOME in Metaspace", null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun actionPerformed(e: AnActionEvent) {
    if (e.modifiers and SHIFT_MASK == 0) {
      val array = arrayOfNulls<Any>(Integer.MAX_VALUE)
      for (i in array.indices) {
        array[i] = arrayOfNulls<Any>(Integer.MAX_VALUE)
      }
      throw OutOfMemoryError()
    }
    else {
      throw OutOfMemoryError("foo Metaspace foo")
    }
  }
}

@Suppress("HardCodedStringLiteral")
internal class SimulateFreeze : DumbAwareAction("Simulate a Freeze") {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val durationString = Messages.showInputDialog(
      e.project,
      "Enter freeze duration in ms",
      "Freeze Simulator",
      null,
      "",
      object : InputValidator {
        override fun checkInput(inputString: String?): Boolean = StringUtil.parseInt(inputString, -1) > 0
        override fun canClose(inputString: String?): Boolean = StringUtil.parseInt(inputString, -1) > 0
      }) ?: return
    simulatedFreeze(durationString.toLong())
  }

  // Keep it a function to detect it in EA
  private fun simulatedFreeze(ms: Long) {
    Thread.sleep(ms)
  }
}
