// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RedundantUnitReturnType")

package com.intellij.platform.backend.observation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.Nls

/**
 * A class that externally observes the state of a configuration activity performed by an IDE subsystem.
 * It is used to wait for a long configuration process performed for a project.
 *
 * Example implementations:
 * - The indexing subsystem's main component is [com.intellij.openapi.project.DumbService] which is responsible for execution of dumb tasks.
 *   An implementation of this interface for indexing subsystem should indicate whether the indexing subsystem has something to do,
 *   i.e. it is effectively the same as [com.intellij.openapi.project.DumbService.isDumb].
 * - A build system plugin often needs to communicate with an external tool.
 *   The implementation for a build system plugin therefore should indicate if the communication is in progress
 *   or if the configuration is not completed yet.
 *
 * If your subsystem does not already have a way to detect configuration process,
 * you may consider using the platform solution of the configuration process markup,
 * which is available under [MarkupBasedActivityInProgressWitness].
 */
@Experimental
interface ActivityInProgressWitness {

  companion object {
    val EP_NAME: ExtensionPointName<ActivityInProgressWitness> = ExtensionPointName("com.intellij.activityInProgressWitness")
  }

  /**
   * The user-visible name of this predicate.
   */
  val presentableName: @Nls String

  /**
   * Checks if a configuration activity is in progress.
   *
   * Be aware that this method can be called numerous times in various scenarios, thus, it is important to limit its side effects.
   *
   * It is also important to note that it is wrong to reason about the result of this method in isolation from the whole system,
   * since it only checks the state of a subsystem at the moment of invocation.
   * In other words, the usage of this method is prone to [TOCTOU](https://en.wikipedia.org/wiki/Time-of-check_to_time-of-use) bug,
   * as some unrelated activity may trigger another phase of configuration for the current subsystem.
   *
   * @return `true` if there is some activity happening, `false` otherwise
   */
  suspend fun isInProgress(project: Project): Boolean

  /**
   * Suspends the current coroutine until the configuration process of a subsystem is finished.
   *
   * Please note, that this method is intended to be used to avoid the active polling of [isInProgress],
   * and therefore it does **NOT** provide any guarantees about the state of the configuration activities
   * in a subsystem on the moment of resumption.
   *
   * @param project the project which is configured
   */
  suspend fun awaitConfiguration(project: Project): Unit
}