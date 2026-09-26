// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.testFramework.fixtures.multiProjectFixture
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.backend.workspace.WorkspaceModelCache
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestObservation.awaitProjectActivity
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.EventDispatcher
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class AutoReloadWsmCacheInvalidationTest {

  private val testDisposable by disposableFixture()
  private val testRoot by tempPathFixture()
  private val multiProjectFixture by multiProjectFixture()

  private val Project.workspaceModelCache: WorkspaceModelCache
    get() = requireNotNull(WorkspaceModelCache.getInstance(this)?.takeIf { it.enabled }) {
      "Workspace model cache MUST be enabled in this test"
    }

  @BeforeEach
  fun setUp() {
    WorkspaceModelCacheImpl.forceEnableCaching(testDisposable)
    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.setMergingTimeSpan(10.milliseconds, testDisposable)
    AutoImportProjectTracker.setAutoReloadDelay(100.milliseconds, testDisposable)
  }

  @Test
  fun `test triggers reload when WSM cache was invalidated on re-open`(): Unit = timeoutRunBlocking {
    val mockProjectAware = MockProjectAware(testRoot)

    multiProjectFixture.openProject(testRoot)
      .useProjectAsync(save = true) { project ->
        initProjectAwareForProject(project, mockProjectAware)
        assertSyncCountAndReset(mockProjectAware, expectedSyncCount = 1) {
          "Expected auto-sync on initial project open"
        }
        project.workspaceModelCache.invalidateCaches()
      }

    multiProjectFixture.openProject(testRoot)
      .useProjectAsync { project ->
        initProjectAwareForProject(project, mockProjectAware)
        assertSyncCountAndReset(mockProjectAware, expectedSyncCount = 1) {
          "Unexpected auto-sync on secondary project open with invalidated caches"
        }
      }
  }

  @Disabled("AT-4013")
  @Test
  fun `test no extra reload when WSM cache persists`(): Unit = timeoutRunBlocking {
    val mockProjectAware = MockProjectAware(testRoot)

    multiProjectFixture.openProject(testRoot)
      .useProjectAsync(save = true) { project ->
        initProjectAwareForProject(project, mockProjectAware)
        assertSyncCountAndReset(mockProjectAware, expectedSyncCount = 1) {
          "Expected auto-sync on initial project open"
        }
      }

    //No invalidation

    multiProjectFixture.openProject(testRoot)
      .useProjectAsync { project ->
        initProjectAwareForProject(project, mockProjectAware)
        assertSyncCountAndReset(mockProjectAware, expectedSyncCount = 0) {
          "Unexpected auto-sync on secondary project open with caches"
        }
      }
  }

  suspend fun initProjectAwareForProject(project: Project, projectAware: ExternalSystemProjectAware) {
    awaitProjectActivity(project) {
      val projectTracker = ExternalSystemProjectTracker.getInstance(project)
      projectTracker.register(projectAware)
      projectTracker.activate(projectAware.projectId)
    }
  }

  private fun assertSyncCountAndReset(mockProjectAware: MockProjectAware, expectedSyncCount: Int, messageSupplier: () -> String) {
    assertEquals(expectedSyncCount, mockProjectAware.syncCount.get(), messageSupplier)
    mockProjectAware.syncCount.set(0)
  }

  private class MockProjectAware(projectRoot: Path) : ExternalSystemProjectAware {

    val syncCount = AtomicInteger(0)
    private val syncDispatcher = EventDispatcher.create(ExternalSystemProjectListener::class.java)

    override val projectId = ExternalSystemProjectId(ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID, projectRoot.toCanonicalPath())
    override val settingsFiles: Set<String> = setOf(projectRoot.resolve("mock.txt").toCanonicalPath())

    override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
      syncDispatcher.addListener(listener, parentDisposable)
    }

    override fun reloadProject(context: ExternalSystemProjectReloadContext) {
      syncDispatcher.multicaster.onProjectReloadStart()
      syncCount.incrementAndGet()
      syncDispatcher.multicaster.onProjectReloadFinish(ExternalSystemRefreshStatus.SUCCESS)
    }
  }
}
