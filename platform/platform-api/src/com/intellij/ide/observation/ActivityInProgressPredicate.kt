// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RedundantUnitReturnType")

package com.intellij.ide.observation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * A predicate that represents the state of a configuration activity performed by an IDE subsystem.
 * It is used to wait for a long configuration process for a project.
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
 * you may consider using [com.intellij.ide.observation.AbstractInProgressService]
 */
@Experimental
interface ActivityInProgressPredicate {

  companion object {
    val EP_NAME : ExtensionPointName<ActivityInProgressPredicate> = ExtensionPointName("com.intellij.activityInProgressPredicate")
  }

  /**
   * The name of this predicate.
   * This identifier is user-visible, and it can be referred to programmatically.
   */
  val presentableName: @NlsSafe String

  /**
   * Checks if a configuration activity is in progress.
   * Be aware that this method can be called numerous times in various scenarios, thus, it is important to limit its side effects.
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
   * In other words, the usage of this method is prone to [TOCTOU](https://en.wikipedia.org/wiki/Time-of-check_to_time-of-use) bug,
   * as some unrelated activity may trigger another phase of configuration for the current subsystem.
   *
   * @param project the project which is configured
   */
  suspend fun awaitFinished(project: Project) : Unit {}
}