// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildId
import com.intellij.execution.process.ProcessOutputType
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable
import java.lang.AutoCloseable
import java.util.function.Consumer

/**
 * Makes the build view events from the output of an external build process.
 *
 * Orchestrates everything that processes and renders the build output:
 *  * the markup of the build output, that attributes its text to the tasks reported by the build events;
 *  * the output parsers, that mark up the errors in the output, provide explanation and suggest quick fixes.
 *
 * @author Vladislav.Soroka
 */
@Internal
@NonExtendable
interface BuildEventDispatcher : AutoCloseable {

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
  fun invokeOnCompletion(consumer: Consumer<in Throwable?>)

  /**
   * Executes completion handlers and finalizes the build process orchestration.
   */
  override fun close()
}
