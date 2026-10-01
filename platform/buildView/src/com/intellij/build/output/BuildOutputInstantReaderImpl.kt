// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildId
import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputMulticaster.Companion.asMulticaster
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

/**
 * @author Vladislav.Soroka
 */
@Suppress("RAW_RUN_BLOCKING")
open class BuildOutputInstantReaderImpl @JvmOverloads constructor(
  buildId: BuildId,
  private val parentEventId: StartId,
  buildProgressListener: BuildProgressListener,
  parsers: List<BuildOutputParser>,
  pushBackBufferSize: Int = 50,
  channelBufferCapacity: Int = 64,
) : BuildOutputInstantReader, Closeable, Appendable {

  private val multicaster = buildProgressListener.asMulticaster(buildId)
  private val lineParsers = parsers.map { it.asLineParser(BuildViewSettingsProvider.EMPTY) }
  private val dispatcher = BuildOutputParserDispatcherImpl(parentEventId, lineParsers, multicaster, channelBufferCapacity, pushBackBufferSize)

  override fun getParentEventId(): StartId =
    parentEventId

  override fun append(csq: CharSequence): BuildOutputInstantReaderImpl = apply {
    runBlocking {
      dispatcher.notifyTextAvailable(BuildOutputText.createOutputText(csq.toString()))
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
      dispatcher.reader.readLine()?.text
    }

  override fun pushBack(): Unit =
    dispatcher.reader.pushBack(1)

  override fun pushBack(numberOfLines: Int): Unit =
    dispatcher.reader.pushBack(numberOfLines)

  @Internal
  abstract class BuildOutputLineParserBridge : BuildOutputParser, BuildOutputLineParser {

    final override fun parse(
      line: @NlsSafe String,
      reader: BuildOutputInstantReader,
      messageConsumer: Consumer<in BuildEvent>,
    ): Boolean = runBlockingCancellable {
      parse(
        BuildOutputText.createOutputText(line),
        reader.asLineReader(),
        reader.parentEventId,
        messageConsumer.asMulticaster(),
        isSingleFlow = false
      )
    }

    final override suspend fun parse(
      line: BuildOutputText,
      reader: BuildOutputReplayableLineReader,
      taskId: StartId,
      multicaster: BuildOutputMulticaster,
    ): Boolean = parse(
      line, reader, taskId, multicaster,
      isSingleFlow = true
    )

    abstract suspend fun parse(
      line: BuildOutputText,
      reader: BuildOutputReplayableLineReader,
      taskId: StartId,
      multicaster: BuildOutputMulticaster,
      isSingleFlow: Boolean,
    ): Boolean
  }

  private class BuildOutputLineParserWrapper(
    private val parser: BuildOutputParser,
    private val settings: BuildViewSettingsProvider,
  ) : BuildOutputLineParser {
    override suspend fun parse(
      line: BuildOutputText,
      reader: BuildOutputReplayableLineReader,
      taskId: StartId,
      multicaster: BuildOutputMulticaster,
    ): Boolean {
      if (parser is BuildOutputLineParserBridge) {
        return parser.parse(line, reader, taskId, multicaster, settings.isSingleBuildConsoleView)
      }
      return parser.parse(line.text, reader.asInstantReader(taskId), multicaster.asEventConsumer())
    }
  }

  private class BuildOutputInstantReaderWrapper(
    val reader: BuildOutputReplayableLineReader,
    private val taskId: StartId,
  ) : BuildOutputInstantReader {

    override fun getParentEventId(): StartId = taskId

    override fun readLine(): String? =
      runBlockingCancellable { reader.readLine()?.text }

    override fun pushBack(): Unit =
      pushBack(1)

    override fun pushBack(numberOfLines: Int): Unit =
      reader.pushBack(numberOfLines)
  }

  private class BuildOutputReplayableLineReaderWrapper(
    val reader: BuildOutputInstantReader,
  ) : BuildOutputReplayableLineReader {
    override fun pushBack(numberOfLines: Int) {
      reader.pushBack(numberOfLines)
    }

    override suspend fun readLine(): BuildOutputText? {
      val line = reader.readLine() ?: return null
      return BuildOutputText.createOutputText(line)
    }
  }

  private class BuildOutputEventConsumerWrapper(
    val multicaster: BuildOutputMulticaster,
  ) : Consumer<BuildEvent> {
    override fun accept(event: BuildEvent) {
      multicaster.notifyBuildEvent(event)
    }
  }

  private class BuildOutputMulticasterWrapper(
    val consumer: Consumer<in BuildEvent>,
  ) : BuildOutputMulticaster {
    override fun notifyBuildEvent(event: BuildEvent) {
      consumer.accept(event)
    }
  }

  @Internal
  companion object {

    @Internal
    fun BuildOutputParser.asLineParser(settings: BuildViewSettingsProvider): BuildOutputLineParser =
      BuildOutputLineParserWrapper(this, settings)

    @Internal
    fun BuildOutputReplayableLineReader.asInstantReader(taskId: StartId): BuildOutputInstantReader =
      when (this) {
        is BuildOutputReplayableLineReaderWrapper -> reader
        else -> BuildOutputInstantReaderWrapper(this, taskId)
      }

    @Internal
    fun BuildOutputInstantReader.asLineReader(): BuildOutputReplayableLineReader =
      when (this) {
        is BuildOutputInstantReaderWrapper -> reader
        else -> BuildOutputReplayableLineReaderWrapper(this)
      }

    @Internal
    fun BuildOutputMulticaster.asEventConsumer(): Consumer<in BuildEvent> =
      when (this) {
        is BuildOutputMulticasterWrapper -> consumer
        else -> BuildOutputEventConsumerWrapper(this)
      }

    @Internal
    fun Consumer<in BuildEvent>.asMulticaster(): BuildOutputMulticaster =
      when (this) {
        is BuildOutputEventConsumerWrapper -> multicaster
        else -> BuildOutputMulticasterWrapper(this)
      }
  }
}