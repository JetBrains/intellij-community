// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import java.io.Closeable
import java.util.function.Consumer

/**
 * Parses, converts build process events and text messages into progress events,
 * and dispatches them into IDEA build progress.
 * @see BuildProgressListener
 */
interface ExternalSystemOutputMessageDispatcher : Closeable, Appendable, BuildProgressListener {
  /**
   * Output type for next [append] and [onEvent] calls.
   * True value means process standard output stream [com.intellij.execution.process.ProcessOutputType.STDOUT]
   * False value means process standard error stream [com.intellij.execution.process.ProcessOutputType.STDERR]
   *
   * @see Appendable
   * @see BuildProgressListener
   */
  var stdOut: Boolean

  /**
   * Subscribes on output close event.
   * @param handler is called when output is closed.
   */
  fun invokeOnCompletion(handler: Consumer<in Throwable?>)
}