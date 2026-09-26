// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.backend.observation.ActivityTracker
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.openProjectAsync
import com.intellij.util.ref.GCUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class ConfigurationAwaiterCloseLeakTest {
  private val tempDir by tempPathFixture()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  @Timeout(30)
  fun `closing project releases configuration waiter`(): Unit = timeoutRunBlocking(20.seconds) {
    val tracker = HangingActivityTracker()
    ExtensionTestUtil.maskExtensions(ACTIVITY_TRACKER_EP, listOf(tracker), disposable, fireEvents = false)

    val projectReference = closeProjectDuringConfigurationAwait(tracker)

    withTimeout(10.seconds) {
      while (projectReference.get() != null) {
        yield()
        GCUtil.tryGcSoftlyReachableObjects()
      }
    }
    assertThat(projectReference.get()).isNull()
  }

  private suspend fun CoroutineScope.closeProjectDuringConfigurationAwait(tracker: HangingActivityTracker): WeakReference<com.intellij.openapi.project.Project> {
    val project = openProjectAsync(tempDir)
    val awaitJob = launch {
      ConfigurationAwaiter.awaitConfiguration(project)
    }
    try {
      tracker.awaitEntered.await()

      project.closeProjectAsync()
      withTimeout(1.seconds) {
        awaitJob.join()
      }

      return WeakReference(project)
    }
    finally {
      tracker.release.complete(Unit)
      awaitJob.cancelAndJoin()
      if (!project.isDisposed) {
        project.closeProjectAsync()
      }
    }
  }

  private class HangingActivityTracker : ActivityTracker {
    val awaitEntered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    private var active = true

    override val presentableName: String = "Hanging test activity"

    override suspend fun isInProgress(project: com.intellij.openapi.project.Project): Boolean = active

    override suspend fun awaitConfiguration(project: com.intellij.openapi.project.Project) {
      awaitEntered.complete(Unit)
      release.await()
      active = false
    }
  }

  private companion object {
    val ACTIVITY_TRACKER_EP: ExtensionPointName<ActivityTracker> = ExtensionPointName.create("com.intellij.activityTracker")
  }
}
