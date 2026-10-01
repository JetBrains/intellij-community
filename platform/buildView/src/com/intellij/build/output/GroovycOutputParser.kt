// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.lang.LangBundle
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.function.Consumer

@ApiStatus.Internal
class GroovycOutputParser : BuildOutputParser {

  private companion object {
    private val FILE_LINE = "(.+\\.groovy): (\\d+): (.*)".toRegex()
    private val LOCATION = "@ line (\\d+), column (\\d+)\\.".toRegex()
  }

  override fun parse(line: @NlsSafe String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    val head = FILE_LINE.matchEntire(line) ?: return false
    val (filePath, rowText, rest) = head.destructured

    val path = try {
      Path.of(filePath)
    }
    catch (_: InvalidPathException) {
      return false
    }
    if (!Files.isRegularFile(path)) {
      return false
    }

    var location = LOCATION.find(rest)
    @NlsSafe val message: String
    @NlsSafe val detailedMessage: String
    if (location != null) {
      message = rest.substring(0, location.range.first).trim()
      detailedMessage = line
    }
    else {
      val next = reader.readLine() ?: return false
      location = LOCATION.find(next)
      if (location == null) {
        reader.pushBack()
        return false
      }
      message = rest.trim()
      detailedMessage = "$line\n$next"
    }

    val row = rowText.toIntOrNull() ?: return false
    val column = location.groupValues[2].toIntOrNull() ?: return false
    messageConsumer.accept(
      MessageEvent.builder(message, MessageEvent.Kind.ERROR)
        .withParentId(reader.parentEventId)
        .withDescription(detailedMessage)
        .withGroup(LangBundle.message("build.event.title.compiler"))
        .withFilePosition(FilePosition(path, row - 1, column - 1))
        .build()
    )
    return true
  }
}
