// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ExternalSystemOperationTestUtil")

package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.withProjectAsync
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.Nls
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

val DEFAULT_SYNC_TIMEOUT: Duration = 10.minutes

@JvmField
val DEFAULT_SYNC_TIMEOUT_MS: Long = DEFAULT_SYNC_TIMEOUT.inWholeMilliseconds

suspend fun awaitOpenProjectActivity(openProject: suspend () -> Project): Project {
  return openProject().withProjectAsync { project ->
    TestObservation.awaitConfiguration(project, DEFAULT_SYNC_TIMEOUT)
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
  }
}

suspend fun <R> awaitProjectActivity(project: Project, action: suspend () -> R): R {
  try {
    return project.trackActivity(TestProjectActivityKey, action)
  }
  finally {
    TestObservation.awaitConfiguration(project, DEFAULT_SYNC_TIMEOUT)
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
  }
}

@Obsolete
fun waitForProjectActivity(project: Project, action: Runnable): Unit =
  waitForProjectActivity(project, action::run)

@Obsolete
fun <R> waitForProjectActivity(project: Project, action: () -> R): R {
  try {
    return project.trackActivityBlocking(TestProjectActivityKey, action)
  }
  finally {
    TestObservation.waitForConfiguration(project, DEFAULT_SYNC_TIMEOUT_MS)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }
}

private object TestProjectActivityKey : ActivityKey {
  override val presentableName: @Nls String
    get() = "The test project activity"
}