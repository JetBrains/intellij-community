// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.LogMessage
import org.jetbrains.intellij.build.impl.BuildUtils
import java.util.function.BiFunction

class ConsoleBuildMessageLogger(parallelTaskId: String) : BuildMessageLoggerBase(parallelTaskId) {
  companion object {
    @JvmField
    val FACTORY = BiFunction<String, AntTaskLogger, BuildMessageLogger> { taskName, _ -> ConsoleBuildMessageLogger(taskName) }

    private val out = BuildUtils.realSystemOut
  }

  override fun processMessage(message: LogMessage) {
    // reported by trace exporter
    if (message.kind != LogMessage.Kind.BLOCK_STARTED && message.kind != LogMessage.Kind.BLOCK_FINISHED) {
      super.processMessage(message)
    }
  }

  override fun shouldBePrinted(kind: LogMessage.Kind) = kind != LogMessage.Kind.DEBUG

  override fun printLine(line: String) {
    out.println(line)
  }
}
