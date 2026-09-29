// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.OverrideOnly

/**
 * Factory of [ExternalSystemOutputMessageDispatcher]s for external system task.
 *
 * @see ExternalSystemOutputMessageDispatcher
 * @see com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
 */
@Internal
@OverrideOnly
interface ExternalSystemOutputDispatcherFactory {
  /**
   * External system id is needed to find applicable factory for external system task.
   */
  val externalSystemId: ProjectSystemId

  /**
   * Creates output dispatcher for current build with [buildId].
   *
   * @param buildProgressListener is main console progress listener.
   * @param parsers are parsers for messages from text and build events.
   *
   * @see BuildProgressListener
   * @see BuildOutputParser
   */
  fun create(buildId: Any,
             buildProgressListener: BuildProgressListener,
             parsers: List<BuildOutputParser>,
             consoleSettings: BuildViewSettingsProvider): ExternalSystemOutputMessageDispatcher

  companion object {

    val EP_NAME: ExtensionPointName<ExternalSystemOutputDispatcherFactory> = ExtensionPointName.create<ExternalSystemOutputDispatcherFactory>("com.intellij.externalSystemOutputDispatcher")
  }
}