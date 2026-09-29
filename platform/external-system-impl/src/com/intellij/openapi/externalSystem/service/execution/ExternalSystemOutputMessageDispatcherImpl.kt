// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.events.BuildId
import com.intellij.build.output.BuildOutputInstantReaderImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.execution.process.ProcessOutputType
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class ExternalSystemOutputMessageDispatcherImpl(
  buildId: BuildId,
  listener: BuildProgressListener,
  parsers: List<BuildOutputParser>,
  consoleSettings: BuildViewSettingsProvider,
) : AbstractExternalSystemOutputMessageDispatcher(buildId, listener, parsers, consoleSettings) {

  private val reader = BuildOutputInstantReaderImpl(buildId, buildId, ::notifyBuildEvent, parsers)

  override fun notifyTextAvailable(text: String, outputType: ProcessOutputType) {
    reader.append(text)
  }

  override fun doClose() {
    reader.close()
  }
}