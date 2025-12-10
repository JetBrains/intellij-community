// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.output.BuildOutputMulticaster.Companion.asMulticaster
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.Closeable
import java.util.LinkedList
import java.util.concurrent.CompletableFuture

/**
 * @author Vladislav.Soroka
 */
open class BuildOutputInstantReaderImpl @JvmOverloads constructor(
  buildId: Any,
  parentEventId: Any,
  buildProgressListener: BuildProgressListener,
  parsers: List<BuildOutputParser>,
  pushBackBufferSize: Int = 50,
  channelBufferCapacity: Int = 64,
) : BuildOutputInstantReader, Closeable, Appendable {

  private val dispatcher = BuildOutputParserDispatcherImpl(
    parentEventId, parsers, buildProgressListener.asMulticaster(buildId), pushBackBufferSize, channelBufferCapacity
  )

  override fun getParentEventId(): Any =
    dispatcher.parentEventId

  override fun append(csq: CharSequence): BuildOutputInstantReaderImpl =
    apply { dispatcher.notifyTextAvailable(csq, ProcessOutputType.STDOUT) }

  override fun append(csq: CharSequence, start: Int, end: Int): BuildOutputInstantReaderImpl =
    apply { dispatcher.notifyTextAvailable(csq.subSequence(start, end), ProcessOutputType.STDOUT) }

  override fun append(c: Char): BuildOutputInstantReaderImpl =
    apply { dispatcher.notifyTextAvailable(c.toString(), ProcessOutputType.STDOUT) }

  override fun close() {
    closeAndGetFuture()
  }

  open fun closeAndGetFuture(): CompletableFuture<Unit> =
    dispatcher.launchDispose()

  override fun readLine(): @NlsSafe String? =
    dispatcher.readLine()

  override fun pushBack(): Unit =
    dispatcher.pushBack(1)

  override fun pushBack(numberOfLines: Int): Unit =
    dispatcher.pushBack(numberOfLines)

  @Experimental
  fun disableActiveReading(): Unit =
    dispatcher.disableActiveReading()

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