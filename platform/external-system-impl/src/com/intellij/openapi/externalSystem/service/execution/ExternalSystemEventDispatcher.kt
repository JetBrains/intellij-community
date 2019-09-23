// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildEventDispatcher
import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.output.BuildOutputInstantReaderImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.util.SmartList
import com.intellij.util.containers.toMutableSmartList
import org.jetbrains.annotations.ApiStatus
import java.io.Closeable
import java.util.function.Consumer

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
class ExternalSystemEventDispatcher(taskId: ExternalSystemTaskId,
                                    progressListener: BuildProgressListener?,
                                    appendOutputToMainConsole: Boolean) : BuildEventDispatcher {
  constructor(taskId: ExternalSystemTaskId, progressListener: BuildProgressListener?) : this(taskId, progressListener, true)

  private lateinit var outputMessageDispatcher: ExternalSystemOutputMessageDispatcher
  private var isStdOut: Boolean = true

  override fun setStdOut(stdOut: Boolean) {
    this.isStdOut = stdOut
    outputMessageDispatcher.stdOut = stdOut
  }

  init {
    val buildOutputParsers = SmartList<BuildOutputParser>()
    if (progressListener != null) {
      ExternalSystemOutputParserProvider.EP_NAME.extensions.forEach {
        if (taskId.projectSystemId == it.externalSystemId) {
          buildOutputParsers.addAll(it.getBuildOutputParsers(taskId))
        }
      }

      var foundFactory: ExternalSystemOutputDispatcherFactory? = null
      EP_NAME.extensions.forEach {
        if (taskId.projectSystemId == it.externalSystemId) {
          if (foundFactory != null) {
            throw RuntimeException("'" + EP_NAME.name + "' extension should be one per external system")
          }
          foundFactory = it
        }
      }
      outputMessageDispatcher = foundFactory?.create(taskId, progressListener, appendOutputToMainConsole, buildOutputParsers)
                                ?: DefaultOutputMessageDispatcher(taskId, progressListener, buildOutputParsers)
    }
  }

  override fun onEvent(buildId: Any, event: BuildEvent) {
    outputMessageDispatcher.onEvent(buildId, event)
  }

  override fun invokeOnCompletion(consumer: Consumer<Throwable?>) {
    outputMessageDispatcher.invokeOnCompletion(consumer)
  }

  override fun append(csq: CharSequence): BuildEventDispatcher? {
    outputMessageDispatcher.append(csq)
    return this
  }

  override fun append(csq: CharSequence, start: Int, end: Int): BuildEventDispatcher? {
    outputMessageDispatcher.append(csq, start, end)
    return this
  }

  override fun append(c: Char): BuildEventDispatcher? {
    outputMessageDispatcher.append(c)
    return this
  }

  override fun close() {
    outputMessageDispatcher.close()
  }

  companion object {
    private val EP_NAME = ExtensionPointName.create<ExternalSystemOutputDispatcherFactory>("com.intellij.externalSystemOutputDispatcher")
  }
}

private class DefaultOutputMessageDispatcher(buildId: Any,
                                             private val buildProgressListener: BuildProgressListener,
                                             parsers: List<BuildOutputParser>) :
  BuildOutputInstantReaderImpl(buildId, buildId, buildProgressListener, parsers), ExternalSystemOutputMessageDispatcher {
  private val onCompletionHandlers = SmartList<Consumer<Throwable?>>()
  override var stdOut: Boolean = true

  override fun onEvent(buildId: Any, event: BuildEvent) =
    when (event) {
      is FinishBuildEvent -> invokeOnCompletion(Consumer { buildProgressListener.onEvent(buildId, event) })
      else -> buildProgressListener.onEvent(buildId, event)
    }

  override fun invokeOnCompletion(handler: Consumer<Throwable?>) {
    onCompletionHandlers.add(handler)
  }

  override fun close() {
    val future = closeAndGetFuture()
    val handlers = onCompletionHandlers.toMutableSmartList()
    onCompletionHandlers.clear()
    for (handler in handlers) {
      future.whenComplete { _, u -> handler.accept(u) }
    }
  }
}

interface ExternalSystemOutputDispatcherFactory {
  val externalSystemId: Any?
  fun create(buildId: Any,
             buildProgressListener: BuildProgressListener,
             appendOutputToMainConsole: Boolean,
             parsers: List<BuildOutputParser>): ExternalSystemOutputMessageDispatcher
}

interface ExternalSystemOutputMessageDispatcher : Closeable, Appendable, BuildProgressListener {
  var stdOut: Boolean
  fun invokeOnCompletion(handler: Consumer<Throwable?>)
}
