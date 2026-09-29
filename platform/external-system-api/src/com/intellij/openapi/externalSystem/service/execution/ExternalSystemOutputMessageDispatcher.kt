// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildId
import com.intellij.execution.process.ProcessOutputType
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import java.lang.AutoCloseable
import java.util.function.Consumer

/**
 * The external system specific output orchestration, that extends the default `BuildEventDispatcher`.
 *
 * For example, attributes the output text to the tasks recognized in that output.
 *
 * @see ExternalSystemOutputDispatcherFactory
 */
@Internal
@OverrideOnly
interface ExternalSystemOutputMessageDispatcher : AutoCloseable {

  /**
   * Dispatches an event of the build process.
   */
  fun notifyBuildEvent(buildId: BuildId, event: BuildEvent)

  /**
   * Dispatches a chunk of the build process output.
   *
   * @param outputType the output stream that produced the `text`.
   */
  fun notifyTextAvailable(text: String, outputType: ProcessOutputType)

  /**
   * Registers handler which is invoked once the build process is finished and the build messages are dispatched.
   */
  fun invokeOnCompletion(handler: Consumer<in Throwable?>)

  /**
   * Executes completion handlers and finalizes the build process orchestration.
   */
  override fun close()
}