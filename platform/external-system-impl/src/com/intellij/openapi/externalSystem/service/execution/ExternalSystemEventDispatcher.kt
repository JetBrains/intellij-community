// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildEventDispatcher
import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.events.BuildEvent
import com.intellij.build.output.BuildOutputParser
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputDispatcherFactory.Companion.EP_NAME
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Internal
class ExternalSystemEventDispatcher(
  taskId: ExternalSystemTaskId,
  progressListener: BuildProgressListener?,
  consoleSettings: BuildViewSettingsProvider,
) : BuildEventDispatcher {

  private lateinit var outputMessageDispatcher: ExternalSystemOutputMessageDispatcher

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
      outputMessageDispatcher =
        foundFactory?.create(taskId, progressListener, buildOutputParsers, consoleSettings)
        ?: ExternalSystemOutputMessageDispatcherImpl(taskId, progressListener, buildOutputParsers, consoleSettings)
    }
  }

  override fun notifyBuildEvent(buildId: Any, event: BuildEvent) {
    outputMessageDispatcher.notifyBuildEvent(buildId, event)
  }

  override fun notifyTextAvailable(text: String, outputType: ProcessOutputType) {
    outputMessageDispatcher.notifyTextAvailable(text, outputType)
  }

  override fun invokeOnCompletion(consumer: Consumer<in Throwable?>) {
    outputMessageDispatcher.invokeOnCompletion(consumer)
  }

  override fun close() {
    outputMessageDispatcher.close()
  }
}
