// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.events.BuildId
import com.intellij.build.events.OutputId
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.output.BuildOutputParserDispatcher
import com.intellij.build.output.BuildOutputText
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentHashMap

@Internal
class ExternalSystemOutputMessageDispatcherImpl(
  buildId: BuildId,
  listener: BuildProgressListener,
  parsers: List<BuildOutputParser>,
  consoleSettings: BuildViewSettingsProvider,
) : AbstractExternalSystemOutputMessageDispatcher(buildId, listener, parsers, consoleSettings) {

  private val parserDispatchers: MutableMap<ProcessOutputType, BuildOutputParserDispatcher> = ConcurrentHashMap()

  override fun notifyTextAvailable(text: @NlsSafe String, outputType: ProcessOutputType) {
    val outputId = OutputId()

    notifyBuildEvent(buildId, OutputBuildEvent.builder(text)
      .withId(outputId)
      .withOutputType(outputType)
      .build())

    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      parserDispatchers.getOrPut(outputType) { createParserDispatcher(buildId) }
        .notifyTextAvailable(BuildOutputText.createOutputText(outputId, text))
    }
  }

  override fun doClose() {
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      for (parserDispatcher in parserDispatchers.values) {
        parserDispatcher.close()
      }
    }
  }
}