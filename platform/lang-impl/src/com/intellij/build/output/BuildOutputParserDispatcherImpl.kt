// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.BuildEvent
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.lang.LangBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ConcurrencyUtil.underThreadNameRunnable
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.util.LinkedList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * @author Vladislav.Soroka
 */
@Internal
class BuildOutputParserDispatcherImpl(
  val parentEventId: Any,
  buildOutputParsers: List<BuildOutputParser>,
  buildOutputMulticaster: BuildOutputMulticaster,
  private val pushBackBufferSize: Int = 50,
  channelBufferCapacity: Int = 64,
) {

  private val channel = LinkedBlockingQueue<BuildOutputLine>(channelBufferCapacity)
  private val readLinesBuffer = LinkedList<BuildOutputLine>()
  private var readLinesBufferPosition = -1
  private val state = AtomicReference(State.Idle)

  @Volatile
  private var useActiveReading = true
  private var readFinishedFuture = CompletableFuture<Unit>()

  private val readerRunnable = underThreadNameRunnable(
    LangBundle.message("thread.name.reader.thread.for.buildoutputinstantreaderimpl.0", System.identityHashCode(parentEventId))) {
    require(!readFinishedFuture.isDone) { LangBundle.message("error.can.t.read.from.closed.stream") }

    val lastEvent = AtomicReference<BuildEvent>(null)
    fun messageConsumer(event: BuildEvent) {
      //do not add duplicates, e.g. sometimes same messages can be added both to stdout and stderr
      if (event == lastEvent.getAndSet(event)) {
        return
      }
      buildOutputMulticaster.notifyBuildEvent(event)
    }

    try {
      while (true) {
        val line = doReadLine(useActiveReading) ?: break
        if (line.isBlank()) continue
        for (parser in buildOutputParsers) {
          val readerWrapper = BuildOutputInstantReaderWrapper(this)
          try {
            if (parser.parse(line, readerWrapper, ::messageConsumer)) break
          }
          catch (e: Exception) {
            when {
              LOG.isDebugEnabled -> LOG.warn("Build output parser error", e)
              else -> LOG.warn("Build output parser error: ${e.message}")
            }
          }
          readerWrapper.pushAllBack()
        }
      }
    }
    catch (ex: Throwable) {
      when {
        LOG.isDebugEnabled -> LOG.warn("Build output reading error", ex)
        else -> LOG.warn("Build output reading error: ${ex.message}")
      }
    }
    finally {
      if (!state.compareAndSet(State.Running, State.Idle)) {
        readFinishedFuture.complete(Unit)
      }
    }
  }

  private val buildOutputLineDispatcher = BuildOutputLineDispatcher { text, payload ->
    notifyLineAvailable(BuildOutputLine(text, payload))
  }

  fun notifyTextAvailable(text: CharSequence, outputType: Key<*>) {
    buildOutputLineDispatcher.notifyTextAvailable(text, BuildOutputData(outputType))
  }

  fun notifyLineAvailable(line: BuildOutputLine) {
    require(state.get() != State.Closed) { LangBundle.message("error.can.t.append.to.closed.stream", line) }
    try {
      while (state.get() != State.Closed) {
        if (state.compareAndSet(State.Idle, State.Running)) {
          ProcessIOExecutorService.INSTANCE.submit(readerRunnable)
        }
        if (channel.offer(line, 100, TimeUnit.MILLISECONDS)) {
          break
        }
      }
    }
    catch (e: InterruptedException) {
      throw IOException(e)
    }
  }

  fun launchDispose(): CompletableFuture<Unit> {
    buildOutputLineDispatcher.close()

    if (state.get() == State.Closed) return readFinishedFuture
    if (state.compareAndSet(State.Idle, State.Closed)) {
      readFinishedFuture.complete(Unit)
    }
    else {
      state.set(State.Closed)
    }
    return readFinishedFuture
  }

  fun readLine(): String? = doReadLine()

  private fun doReadLine(waitIfNotClosed: Boolean = true): String? {
    if (readLinesBufferPosition >= 0) {
      val line = readLinesBuffer[readLinesBufferPosition]
      readLinesBufferPosition--
      return line.text
    }
    var line: BuildOutputLine?
    while (true) {
      line = channel.poll(100, TimeUnit.MILLISECONDS)
      if (line != null || state.get() == State.Closed) break
      if (!waitIfNotClosed) return null
    }
    if (line == null) return null
    readLinesBuffer.addFirst(line)
    if (readLinesBuffer.size > pushBackBufferSize) {
      readLinesBuffer.removeLast()
    }
    return line.text
  }

  fun pushBack(numberOfLines: Int) {
    readLinesBufferPosition += numberOfLines
    if (readLinesBufferPosition >= pushBackBufferSize) {
      readLinesBufferPosition = pushBackBufferSize - 1
    }
  }

  @Experimental
  fun disableActiveReading() {
    useActiveReading = false
  }

  private class BuildOutputInstantReaderWrapper(
    private val dispatcher: BuildOutputParserDispatcherImpl,
  ) : BuildOutputInstantReader {

    private var linesRead = 0

    override fun getParentEventId(): Any =
      dispatcher.parentEventId

    override fun readLine(): String? {
      val line = dispatcher.readLine()
      if (line != null) linesRead++
      return line
    }

    override fun pushBack() = pushBack(1)

    fun pushAllBack() = pushBack(linesRead)

    override fun pushBack(numberOfLines: Int) {
      val numberToPushBack = minOf(numberOfLines, linesRead)
      linesRead -= numberToPushBack
      dispatcher.pushBack(numberToPushBack)
    }
  }

  data class BuildOutputData(
    val outputType: Key<*>,
  )

  data class BuildOutputLine(
    val text: @NlsSafe String,
    val data: List<BuildOutputData>,
  )

  companion object {
    private val LOG = logger<BuildOutputInstantReader>()

    private enum class State { Idle, Running, Closed }
  }
}
