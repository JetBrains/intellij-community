// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.tests

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import com.intellij.platform.rpc.lite.RemoteApiProvider
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.platform.testFramework.plugins.dependsIntellijModulesLang
import com.intellij.platform.testFramework.plugins.extensions
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.time.Duration.Companion.seconds

/**
 * The lite service installs the provider that [RemoteApiProvider.EP_NAME] holds, at its construction or when the
 * extension appears, and drops it when the extension goes away.
 */
@TestApplication
// The synthetic plugin in this test does not contribute index extensions.
@SystemProperty(propertyKey = "intellij.indexes.skip.reload.on.plugin.load.unload", propertyValue = "true")
@Timeout(60)
internal class LiteRemoteApiProviderServiceTest {
  private val tempDir by tempPathFixture()

  @Test
  fun `the extension present at construction connects at once`(): Unit = timeoutRunBlocking(30.seconds) {
    val disposable = Disposer.newDisposable("extension present")
    try {
      val api = LiteTestApi()
      ExtensionTestUtil.maskExtensions(RemoteApiProvider.EP_NAME, listOf(LiteTestApiProvider(api)), disposable, fireEvents = false)

      val service = LiteRemoteApiProviderService(this)

      assertThat(service.isConnected()).isTrue()
      assertThat(service.tryResolve(LiteTestApiDescriptor)).isSameAs(api)
      assertThat(service.awaitConnectionAndResolve(LiteTestApiDescriptor)).isSameAs(api)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  /**
   * The upgrade of a light process: the module that contributes the provider loads without a restart, and the callers
   * that waited resume. A masked extension point is read-only, so the test hides the extension of
   * `intellij.platform.rpc` and lets the mask disposal bring it back, which fires the same listeners as a load.
   */
  @Test
  fun `an extension that appears later releases the waiting callers`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadLazyTestApiPlugin()
    try {
      val mask = Disposer.newDisposable("no provider")
      ExtensionTestUtil.maskExtensions(RemoteApiProvider.EP_NAME, emptyList(), mask)
      val service = LiteRemoteApiProviderService(this)
      val request = async(start = CoroutineStart.UNDISPATCHED) {
        service.awaitConnectionAndResolve(remoteApiDescriptor<LazyTestApi>())
      }
      assertThat(service.isConnected()).isFalse()
      assertThat(service.tryResolve(remoteApiDescriptor<LazyTestApi>())).isNull()
      assertThat(request.isCompleted).isFalse()

      Disposer.dispose(mask)

      assertThat(service.isConnected()).isTrue()
      assertThat(request.await()).isInstanceOf(LazyTestApi::class.java)
      assertThat(service.tryResolve(remoteApiDescriptor<LazyTestApi>())).isInstanceOf(LazyTestApi::class.java)
    }
    finally {
      withContext(Dispatchers.EDT) {
        Disposer.dispose(plugin)
      }
    }
  }

  /**
   * The move of JetBrains Light back to the light mode: the module that contributes the provider unloads, and the
   * extension goes with it. The mask hides the extension of `intellij.platform.rpc` the way the unload removes it,
   * and the mask disposal brings it back the way a new upgrade does.
   */
  @Test
  fun `an extension that goes away disconnects the service, and its return reconnects`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadLazyTestApiPlugin()
    try {
      val service = LiteRemoteApiProviderService(this)
      assertThat(service.isConnected()).isTrue()

      val mask = Disposer.newDisposable("provider unloaded")
      ExtensionTestUtil.maskExtensions(RemoteApiProvider.EP_NAME, emptyList(), mask)

      assertThat(service.isConnected()).isFalse()
      assertThat(service.tryResolve(remoteApiDescriptor<LazyTestApi>())).isNull()
      val request = async(start = CoroutineStart.UNDISPATCHED) {
        service.awaitConnectionAndResolve(remoteApiDescriptor<LazyTestApi>())
      }
      assertThat(request.isCompleted).isFalse()

      Disposer.dispose(mask)

      assertThat(service.isConnected()).isTrue()
      assertThat(request.await()).isInstanceOf(LazyTestApi::class.java)
    }
    finally {
      withContext(Dispatchers.EDT) {
        Disposer.dispose(plugin)
      }
    }
  }

  /** A dynamic plugin load is a modal operation, so it runs on the EDT. */
  private suspend fun loadLazyTestApiPlugin(): Disposable = withContext(Dispatchers.EDT) {
    loadPluginWithText(
      pluginSpec = plugin {
        dependsIntellijModulesLang()
        extensions("""<platform.rpc.backend.remoteApiProvider implementation="${LazyTestApiProvider::class.java.name}" apiInterfaces="${LazyTestApi::class.java.name}"/>""")
      },
      pluginsDir = tempDir.resolve("plugins"),
    )
  }
}
