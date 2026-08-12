// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.BuildEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference

@Internal
class BuildOutputParserDispatcherImpl(
  private val parentEventId: Any,
  parsers: List<BuildOutputParser>,
  multicaster: BuildOutputMulticaster,
  linesBufferSize: Int = 64,
  pushBackBufferSize: Int = 50,
) : BuildOutputParserDispatcher {

  internal val reader = BuildOutputReplayableLineReaderImpl(linesBufferSize, pushBackBufferSize)

  @OptIn(DelicateCoroutinesApi::class)
  private val coroutineScope = GlobalScope.childScope(BuildOutputParserDispatcherImpl::class.java.name + Dispatchers.IO)

  private val parserAction = coroutineScope.launch {
    try {
      val lastEvent = AtomicReference<BuildEvent>(null)
      fun messageConsumer(event: BuildEvent) {
        //do not add duplicates, e.g. sometimes same messages can be added both to stdout and stderr
        if (event == lastEvent.getAndSet(event)) {
          return
        }
        multicaster.notifyBuildEvent(event)
      }

      while (true) {
        val line = reader.readLine() ?: break
        if (line.isBlank()) continue
        for (parser in parsers) {
          val readerWrapper = BuildOutputInstantReaderWrapper(reader, parentEventId)
          try {
            if (parser.parse(line, readerWrapper, ::messageConsumer)) break
          }
          catch (exception: Exception) {
            rethrowControlFlowException(exception)
            logException("Build output parser error", exception)
          }
          readerWrapper.pushAllBack()
        }
      }
    }
    catch (exception: Exception) {
      reader.close()

      rethrowControlFlowException(exception)
      logException("Build output reading error", exception)
    }
  }

  override suspend fun notifyTextAvailable(text: CharSequence) {
    reader.notifyTextAvailable(text)
  }

  override suspend fun close() {
    reader.close()
    parserAction.join()
  }

  @Suppress("RAW_RUN_BLOCKING")
  private class BuildOutputInstantReaderWrapper(
    private val bufferedReader: BuildOutputReplayableLineReader,
    private val parentEventId: Any,
  ) : BuildOutputInstantReader {

    private var linesRead = 0

    override fun getParentEventId(): Any = parentEventId

    override fun readLine(): String? {
      val line = runBlocking { bufferedReader.readLine() }
      if (line != null) linesRead++
      return line
    }

    override fun pushBack(): Unit = pushBack(1)

    fun pushAllBack(): Unit = pushBack(linesRead)

    override fun pushBack(numberOfLines: Int) {
      val numberToPushBack = minOf(numberOfLines, linesRead)
      linesRead -= numberToPushBack
      bufferedReader.pushBack(numberToPushBack)
    }
  }

  companion object {

    private val LOG = logger<BuildOutputInstantReader>()

    private fun logException(message: String, exception: Exception) {
      when {
        LOG.isDebugEnabled -> LOG.warn(message, exception)
        else -> LOG.warn("$message: ${exception.message}")
      }
    }
  }
}
