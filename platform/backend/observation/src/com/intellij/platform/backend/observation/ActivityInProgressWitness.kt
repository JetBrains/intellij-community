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
 * ## Example implementations
 * - The indexing subsystem's main component is [com.intellij.openapi.project.DumbService] which is responsible for execution of dumb tasks.
 *   An implementation of this interface for indexing subsystem should indicate whether the indexing subsystem has something to do,
 *   i.e. it is effectively the same as [com.intellij.openapi.project.DumbService.isDumb].
 * - A build system plugin often needs to communicate with an external tool.
 *   The implementation for a build system plugin therefore should indicate if the communication is in progress
 *   or if the configuration is not completed yet.
 *
 * ## Template
 * If your subsystem does not already have a way to detect configuration process,
 * you may consider using the platform solution of the configuration process markup,
 * which is available under [MarkupBasedActivityInProgressWitness].
 *
 * ## Contract
 * It is important to note that it is wrong to reason about the result of the provided methods in isolation from the whole system,
 * since they only work with the state of a single subsystem at the moment of invocation.
 * In other words, the usage of the provided methods is prone to [TOCTOU](https://en.wikipedia.org/wiki/Time-of-check_to_time-of-use) bug,
 * as some unrelated activity may trigger another phase of configuration for the current subsystem.
 *
 * ## Side effect freedom
 * Generally, it is highly discouraged to have any side effects in the provided methods.
 * The platform does not give any guarantees on the environment of invocation,
 * so handling the consequences of some state change might be difficult.
 * Nevertheless, the platform is ready to handle *idempotent* side effects (such as saving of files, for example).
 * It means that if there is a configuration process ongoing, the methods will be invoked at least two times.
 */
@Experimental
interface ActivityInProgressWitness {

  companion object {
    val EP_NAME: ExtensionPointName<ActivityInProgressWitness> = ExtensionPointName("com.intellij.activityInProgressWitness")
  }

  /**
   * The user-visible name of this witness.
   */
  val presentableName: @Nls String

  /**
   * Checks if a configuration activity is in progress.
   *
   * See [ActivityInProgressWitness] documentation for more details about the contract and guarantees.
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
   * See [ActivityInProgressWitness] documentation for more details about the contract and guarantees.
   *
   * @param project the project which is configured
   */
  suspend fun awaitConfiguration(project: Project): Unit
}