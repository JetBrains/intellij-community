// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId

/**
 * Factory of [ExternalSystemOutputMessageDispatcher]s for external system task.
 *
 * @see ExternalSystemOutputMessageDispatcher
 * @see com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
 */
interface ExternalSystemOutputDispatcherFactory {
  /**
   * External system id is needed to find applicable factory for external system task.
   */
  val externalSystemId: ProjectSystemId

  /**
   * Creates output dispatcher for current build with [buildId].
   *
   * @param buildProgressListener is main console progress listener.
   * @param appendOutputToMainConsole is flag for output dispatcher.
   * Dispatcher should transfer message events into [buildProgressListener], if this argument is true.
   * @param parsers are parsers for messages from text and build events.
   *
   * @see BuildProgressListener
   * @see BuildOutputParser
   */
  fun create(buildId: Any,
             buildProgressListener: BuildProgressListener,
             appendOutputToMainConsole: Boolean,
             parsers: List<BuildOutputParser>): ExternalSystemOutputMessageDispatcher

  companion object {

    val EP_NAME: ExtensionPointName<ExternalSystemOutputDispatcherFactory> = ExtensionPointName.create<ExternalSystemOutputDispatcherFactory>("com.intellij.externalSystemOutputDispatcher")
  }
}