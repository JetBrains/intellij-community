// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.TimeoutUtil
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.event.InputEvent
import java.util.*

private const val TEST_LOGGER = "TEST.LOGGER"
private const val TEST_MESSAGE = "test exception; please ignore"

private val random = Random()
private fun randomString() = "random exception text ${random.nextLong()}"

class DropAnErrorAction : DumbAwareAction("Drop an error", "Hold down SHIFT for a sequence of exceptions", null) {
  override fun actionPerformed(e: AnActionEvent) {
    if (e.modifiers and InputEvent.SHIFT_MASK == 0) {
      Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception(randomString()))
    }
    else {
      PooledThreadExecutor.INSTANCE.submit {
        for (i in 1..3) {
          Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception(randomString()))
          TimeoutUtil.sleep(200)
        }
      }
    }
  }
}

class DropAnErrorWithAttachmentsAction : DumbAwareAction("Drop an error with attachments", "Hold down SHIFT for multiple attachments", null) {
  override fun actionPerformed(e: AnActionEvent) {
    val attachments = if (e.modifiers and InputEvent.SHIFT_MASK == 0) {
      arrayOf(Attachment("attachment.txt", "content"))
    }
    else {
      arrayOf(Attachment("first.txt", "content"), Attachment("second.txt", "more content"), Attachment("third.txt", "even more content"))
    }
    Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, Exception(randomString()), *attachments)
  }
}

class DropPluginErrorAction : DumbAwareAction("Drop an error in a random plugin", "Hold down SHIFT for 3rd-party plugins only", null) {
  override fun actionPerformed(e: AnActionEvent) {
    var plugins = PluginManager.getPlugins()
    if (e.modifiers and InputEvent.SHIFT_MASK != 0) {
      plugins = plugins.filterNot { PluginManagerMain.isDevelopedByJetBrains(it) }.toTypedArray()
    }
    if (plugins.isNotEmpty()) {
      val victim = plugins[random.nextInt(plugins.size)]
      Logger.getInstance(TEST_LOGGER).error(TEST_MESSAGE, PluginException(randomString(), victim.pluginId))
    }
  }
}

class DropAnOutOfMemoryErrorAction : DumbAwareAction("Drop an OutOfMemoryError", "Hold down SHIFT for OOME in Metaspace", null) {
  override fun actionPerformed(e: AnActionEvent) {
    if (e.modifiers and InputEvent.SHIFT_MASK == 0) {
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