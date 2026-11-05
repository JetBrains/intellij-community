// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.StartId
import com.intellij.openapi.diagnostic.logger
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference

@Internal
class BuildOutputParserDispatcherImpl(
  private val taskId: StartId,
  parsers: List<BuildOutputLineParser>,
  multicaster: BuildOutputMulticaster,
  linesBufferSize: Int = 64,
  pushBackBufferSize: Int = 50,
) : BuildOutputParserDispatcher {

  internal val reader = BuildOutputReplayableLineReaderImpl(linesBufferSize, pushBackBufferSize)

  @OptIn(DelicateCoroutinesApi::class)
  private val coroutineScope = GlobalScope.childScope(name = BuildOutputParserDispatcherImpl::class.java.name, context = Dispatchers.IO)

  private val parserAction = coroutineScope.launch {
    try {
      val lastEvent = AtomicReference<BuildEvent>(null)
      val multicasterWrapper = object : BuildOutputMulticaster {
        override fun notifyBuildEvent(event: BuildEvent) {
          //do not add duplicates, e.g. sometimes same messages can be added both to stdout and stderr
          if (event == lastEvent.getAndSet(event)) {
            return
          }
          multicaster.notifyBuildEvent(event)
        }
      }

      while (true) {
        val line = reader.readLine() ?: break
        if (line.text.isBlank()) continue
        for (parser in parsers) {
          val readerWrapper = BuildOutputReplayableLineReaderWrapper(reader)
          try {
            if (parser.parse(line, readerWrapper, taskId, multicasterWrapper)) break
          }
          catch (throwable: Throwable) {
            rethrowControlFlowException(throwable)
            logException("Build output parser error", throwable)
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

  override suspend fun notifyTextAvailable(text: BuildOutputText) {
    reader.notifyTextAvailable(text)
  }

  override suspend fun close() {
    reader.close()
    parserAction.join()
  }

  private class BuildOutputReplayableLineReaderWrapper(
    private val reader: BuildOutputReplayableLineReader,
  ) : BuildOutputReplayableLineReader {

    private var linesRead = 0

    override suspend fun readLine(): BuildOutputText? {
      val line = reader.readLine()
      if (line != null) linesRead++
      return line
    }

    fun pushAllBack(): Unit = pushBack(linesRead)

    override fun pushBack(numberOfLines: Int) {
      val numberToPushBack = minOf(numberOfLines, linesRead)
      linesRead -= numberToPushBack
      reader.pushBack(numberToPushBack)
    }
  }

  companion object {

    private val LOG = logger<BuildOutputInstantReader>()

    private fun logException(message: String, exception: Throwable) {
      when {
        LOG.isDebugEnabled -> LOG.warn(message, exception)
        else -> LOG.warn("$message: ${exception.message}")
      }
    }
  }
}
