// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test

/** The navigation bar service turns the unload of a plugin into a reset of its static bars. */
@TestApplication
internal class NavBarServicePluginUnloadTest {
  private val projectFixture = projectFixture()

  @Test
  fun `the unload of a plugin makes the service emit a reset`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    project.registerOrReplaceServiceInstance(NavBarServiceDelegate::class.java, IdleNavBarServiceDelegate, disposable)
    val service = NavBarService.getInstance(project)
    val reset = async(start = CoroutineStart.UNDISPATCHED) { service.pluginUnloadEvents.first() }

    service.pluginUnloadListener.pluginUnloaded(checkNotNull(PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)), isUpdate = false)

    reset.await()
  }
}

/** A delegate without activity. The test application loads no navigation bar module of a product, so no delegate is registered. */
private object IdleNavBarServiceDelegate : NavBarServiceDelegate {
  override fun activityFlow(): Flow<Unit> = emptyFlow()

  override suspend fun defaultModel(): NavBarVmItem = error("the test shows no navigation bar")

  override suspend fun contextModel(ctx: DataContext): List<NavBarVmItem> = emptyList()

  override suspend fun navigate(item: NavBarVmItem) {
  }
}
