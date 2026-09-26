// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.TimeoutUtil
import java.awt.event.ActionEvent.CTRL_MASK
import java.awt.event.ActionEvent.META_MASK
import java.awt.event.ActionEvent.SHIFT_MASK
import java.util.Random
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream

private const val TEST_LOGGER = "TEST.LOGGER"
private const val TEST_MESSAGE = "test exception; please ignore"

private val random = Random()
private fun randomString() = "random exception text ${random.nextLong()}"

internal class DropAnErrorAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    if (e.modifiers and SHIFT_MASK == 0) {
      Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception(randomString()))
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread {
        repeat(3) {
          Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception(randomString()))
          TimeoutUtil.sleep(200)
        }
      }
    }
  }
}

@Suppress("HardCodedStringLiteral")
internal class DropAnErrorWithAttachmentsAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val attachments = if (e.modifiers and SHIFT_MASK == 0 && e.modifiers and CTRL_MASK == 0) {
      arrayOf(Attachment("attachment.txt", "content"))
    }
    else if (e.modifiers and SHIFT_MASK != 0) {
      arrayOf(Attachment("first.txt", "content"), Attachment("second.txt", "more content"), Attachment("third.txt", "even more content"))
    }
    else if (e.modifiers and CTRL_MASK != 0) {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), "Creating Attachments") {
        getLargeAttachment()
      }
    }
    else {
      emptyArray<Attachment>()
    }
    Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception(randomString()), *attachments)
  }

  private fun getLargeAttachment(): Array<Attachment> {
    val buffer = ByteArray(1 shl 20)
    val n = 50
    val file = createTempFile(PathManager.getTempDir(), "large-attachment", ".bin")
    file.outputStream().use { out ->
      repeat(n) {
        random.nextBytes(buffer)
        out.write(buffer)
      }
    }
    @Suppress("SSBasedInspection")
    file.toFile().deleteOnExit()
    return arrayOf(Attachment("large.bin", file, "A large attachment of ${n * buffer.size} bytes").apply { isIncluded = true })
  }
}

internal class DropPluginErrorAction : DumbAwareAction() {
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

internal class DropAnOutOfMemoryErrorAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    if (e.modifiers and SHIFT_MASK != 0) {
      throw OutOfMemoryError("Metaspace")
    }
    else if (e.modifiers and (if (ClientSystemInfo.isMac()) META_MASK else CTRL_MASK) != 0) {
      throw OutOfMemoryError("Java heap space")
    }
    else {
      exhaustJavaHeap()
    }
  }

  private fun exhaustJavaHeap(): Nothing {
    val chunks = ArrayList<ByteArray>()
    while (true) {
      chunks.add(ByteArray(8 * 8 * 1024 * 1024))
    }
  }
}
