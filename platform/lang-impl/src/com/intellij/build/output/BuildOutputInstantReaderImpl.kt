// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.output.BuildOutputMulticaster.Companion.asMulticaster
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.Closeable
import java.util.LinkedList
import java.util.concurrent.CompletableFuture

/**
 * @author Vladislav.Soroka
 */
@Suppress("RAW_RUN_BLOCKING")
open class BuildOutputInstantReaderImpl @JvmOverloads constructor(
  buildId: Any,
  private val parentEventId: Any,
  buildProgressListener: BuildProgressListener,
  parsers: List<BuildOutputParser>,
  pushBackBufferSize: Int = 50,
  channelBufferCapacity: Int = 64,
) : BuildOutputInstantReader, Closeable, Appendable {

  private val multicaster = buildProgressListener.asMulticaster(buildId)
  private val dispatcher = BuildOutputParserDispatcherImpl(parentEventId, parsers, multicaster, channelBufferCapacity, pushBackBufferSize)

  override fun getParentEventId(): Any =
    parentEventId

  override fun append(csq: CharSequence): BuildOutputInstantReaderImpl = apply {
    runBlocking {
      dispatcher.notifyTextAvailable(csq)
    }
  }

  override fun append(csq: CharSequence, start: Int, end: Int): BuildOutputInstantReaderImpl =
    append(csq.subSequence(start, end))

  override fun append(c: Char): BuildOutputInstantReaderImpl =
    append(c.toString())

  override fun close() {
    runBlocking {
      dispatcher.close()
    }
  }

  @Deprecated("Use close() instead", ReplaceWith("close()"))
  open fun closeAndGetFuture(): CompletableFuture<Unit> {
    close()
    return CompletableFuture.completedFuture(Unit)
  }

  override fun readLine(): @NlsSafe String? =
    runBlocking {
      dispatcher.reader.readLine()
    }

  override fun pushBack(): Unit =
    dispatcher.reader.pushBack(1)

  override fun pushBack(numberOfLines: Int): Unit =
    dispatcher.reader.pushBack(numberOfLines)
}

@Internal
@Experimental
class BuildOutputCollector(private val reader: BuildOutputInstantReader) : BuildOutputInstantReader {
  private val readLines = LinkedList<String>()
  override fun getParentEventId(): Any = reader.parentEventId

  override fun readLine(): String? {
    val line = reader.readLine()
    if (line != null) {
      readLines.add(line)
    }
    return line
  }

  override fun pushBack() {
    reader.pushBack()
    readLines.pollLast()
  }

  override fun pushBack(numberOfLines: Int) {
    reader.pushBack(numberOfLines)
    repeat(numberOfLines) { readLines.pollLast() ?: return@repeat }
  }

  fun getOutput(): String = readLines.joinToString(separator = "\n")
}