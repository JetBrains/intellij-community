// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.google.gson.GsonBuilder
import com.intellij.codeInspection.ex.InspectionProblemConsumer
import com.intellij.codeInspection.ex.JsonInspectionsReportConverter
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal const val asyncBufferCapacity = 1000
private val LOG = Logger.getInstance(AsyncInspectionToolResultWriter::class.java)

open class AsyncInspectionToolResultWriter(val outputPath: Path): InspectionProblemConsumer {
  val channel: Channel<Element> = Channel(asyncBufferCapacity)

  val writer = GlobalScope.runWriter()

  fun CoroutineScope.runWriter(): Job? {
    val path = outputPath.resolve("results.json")
    val gson = GsonBuilder().setPrettyPrinting().create()
    val writer = Files.newBufferedWriter(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE)

    return launch(Dispatchers.IO) {
      LOG.info("Async result writer started")
      for (message in channel) {
        val jsonWriter = gson.newJsonWriter(writer)
        JsonInspectionsReportConverter.convertProblem(jsonWriter, message)
        writer.write("\n")
        jsonWriter.flush()
      }
      LOG.info("Async result writer finished")
    }
  }

  override fun consume(element: Element) {
    runBlocking(Dispatchers.IO) {
      channel.send(element)
    }
  }
}
