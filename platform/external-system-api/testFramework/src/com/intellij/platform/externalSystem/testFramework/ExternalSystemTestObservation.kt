// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework

import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestObservation
import org.jetbrains.annotations.ApiStatus.Obsolete
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * This timeout covers a single external system (build tool) interaction: sync, build, run, etc.
 * For IDE elements, that doesn't depend on third party code, use the [com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT] instead.
 */
val DEFAULT_EXTERNAL_SYSTEM_TEST_TIMEOUT: Duration = 10.minutes

@JvmField
val DEFAULT_EXTERNAL_SYSTEM_TEST_TIMEOUT_MS: Long = DEFAULT_EXTERNAL_SYSTEM_TEST_TIMEOUT.inWholeMilliseconds

object ExternalSystemTestObservation {

  suspend fun awaitOpenProjectActivity(openProject: suspend () -> Project): Project =
    TestObservation.awaitOpenProjectActivity(DEFAULT_EXTERNAL_SYSTEM_TEST_TIMEOUT, openProject)

  suspend fun <R> awaitProjectActivity(project: Project, action: suspend () -> R): R =
    TestObservation.awaitProjectActivity(project, DEFAULT_EXTERNAL_SYSTEM_TEST_TIMEOUT, action)

  /**
   * obsolete: Use [awaitProjectActivity] instead.
   */
  @Obsolete
  @JvmStatic
  fun waitForProjectActivity(project: Project, action: Runnable): Unit =
    TestObservation.waitForProjectActivity(project, DEFAULT_EXTERNAL_SYSTEM_TEST_TIMEOUT_MS, action)

  /**
   * obsolete: Use [awaitProjectActivity] instead.
   */
  @Obsolete
  @JvmStatic
  fun <R> waitForProjectActivity(project: Project, action: () -> R): R =
    TestObservation.waitForProjectActivity(project, DEFAULT_EXTERNAL_SYSTEM_TEST_TIMEOUT_MS, action)
}