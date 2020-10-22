// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonWriter
import com.intellij.codeInspection.ex.InspectionProblemConsumer
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.JsonInspectionsReportConverter
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jdom.Element
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal const val asyncBufferCapacity = 1000
private val LOG = Logger.getInstance(AsyncInspectionToolResultWriter::class.java)

open class AsyncInspectionToolResultWriter(val outputPath: Path) : InspectionProblemConsumer {
  val channel: Channel<Pair<String, Element>> = Channel(asyncBufferCapacity)

  init {
    GlobalScope.runWriteJob()
  }

  private fun CoroutineScope.runWriteJob(): Job {

    return launch(Dispatchers.IO) {
      LOG.info("Async result writer started")
      val writers = mutableMapOf<String, JsonResultWriter>()

      for ((inspectionId, element) in channel) {
        val resultWriter = writers.computeIfAbsent(inspectionId) { JsonResultWriter(it, outputPath) }
        resultWriter.writeElement(element)
      }
      writers.forEach { (_, writer) -> writer.stop() }
      LOG.info("Async result writer finished")
    }
  }

  override fun consume(element: Element, toolWrapper: InspectionToolWrapper<*, *>) {
    runBlocking(Dispatchers.IO) {
      channel.send(toolWrapper.shortName to element)
    }
  }

  fun close() {
    channel.close()
  }

  class JsonResultWriter(inspectionId: String, outputPath: Path) {
    val path: Path = outputPath.resolve("$inspectionId.json")
    val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    val writer: Writer = Files.newBufferedWriter(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE,
                                                 StandardOpenOption.WRITE)
    var first = true

    init {
      start()
    }

    fun start() {
      writer.write("{\"problems\":[\n")
    }

    fun writeElement(element: Element) {
      if (first) first = false else writer.write(",\n")
      val jsonWriter: JsonWriter = gson.newJsonWriter(writer)
      JsonInspectionsReportConverter.convertProblem(jsonWriter, element)
      jsonWriter.flush()
    }

    fun stop() {
      writer.write("\n]}")
      writer.close()
    }
  }
}
