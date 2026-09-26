// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildEventDispatcher
import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputDispatcherFactory.Companion.EP_NAME
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

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
                                ?: ExternalSystemOutputMessageDispatcherImpl(taskId, progressListener, buildOutputParsers)
    }
  }

  override fun onEvent(buildId: Any, event: BuildEvent) {
    outputMessageDispatcher.onEvent(buildId, event)
  }

  override fun invokeOnCompletion(consumer: Consumer<in Throwable?>) {
    outputMessageDispatcher.invokeOnCompletion(consumer)
  }

  override fun append(csq: CharSequence): ExternalSystemEventDispatcher = apply {
    outputMessageDispatcher.append(csq)
  }

  override fun append(csq: CharSequence, start: Int, end: Int): ExternalSystemEventDispatcher = apply {
    outputMessageDispatcher.append(csq, start, end)
  }

  override fun append(c: Char): ExternalSystemEventDispatcher = apply {
    outputMessageDispatcher.append(c)
  }

  override fun close() {
    outputMessageDispatcher.close()
  }
}
