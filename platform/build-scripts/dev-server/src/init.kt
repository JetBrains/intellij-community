// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer

import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import java.util.logging.*

internal fun initLog() {
  val root = Logger.getLogger("")
  root.level = Level.INFO
  for (handler in root.handlers) {
    root.removeHandler(handler)
  }

  root.addHandler(object : StreamHandler(System.err, object : Formatter() {
    override fun format(record: LogRecord): String {
      val timestamp = String.format("%1\$tT,%1\$tL", record.millis)
      return "$timestamp ${record.message}\n" + (record.thrown?.let { thrown ->
        val writer = StringWriter()
        thrown.printStackTrace(PrintWriter(writer))
        writer.toString()
      } ?: "")
    }
  }) {
    override fun publish(record: LogRecord?) {
      super.publish(record)
      flush()
    }

    override fun close() {
      flush()
    }
  })
}

internal fun getHomePath(): Path {
  return IdeaProjectLoaderUtil.guessUltimateHome(DevIdeaBuildServer::class.java)
}