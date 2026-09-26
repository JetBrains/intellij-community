// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.tests

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.platform.testFramework.plugins.dependsIntellijModulesLang
import com.intellij.platform.testFramework.plugins.extensions
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * A `remoteApiProvider` declaration with `apiInterfaces` is constructed on the first call to one of its APIs, not when the plugin loads.
 * IJPL-254356.
 */
@TestApplication
// Synthetic plugins in this test do not contribute index extensions.
@SystemProperty(propertyKey = "intellij.indexes.skip.reload.on.plugin.load.unload", propertyValue = "true")
class RemoteApiRegistryLazyTest {
  private val tempDir by tempPathFixture()

  @BeforeEach
  fun resetCounters() {
    LazyTestApiProvider.instances.set(0)
    PartiallyDeclaredApiProvider.instances.set(0)
  }

  /** A dynamic plugin load is a modal operation, so it runs on the EDT. */
  private suspend fun loadProvider(providerClass: Class<*>, apiInterfaces: String): Disposable = withContext(Dispatchers.EDT) {
    loadPluginWithText(
      pluginSpec = plugin {
        dependsIntellijModulesLang()
        extensions("""<platform.rpc.backend.remoteApiProvider implementation="${providerClass.name}" apiInterfaces="$apiInterfaces"/>""")
      },
      pluginsDir = tempDir.resolve("plugins"),
    )
  }

  private suspend fun unload(plugin: Disposable) {
    withContext(Dispatchers.EDT) {
      Disposer.dispose(plugin)
    }
  }

  @Test
  @Timeout(60)
  fun `provider is constructed on the first call, and released with the plugin`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadProvider(LazyTestApiProvider::class.java, LazyTestApi::class.java.name)
    try {
      assertThat(LazyTestApiProvider.instances.get()).describedAs("provider instances after plugin load").isZero()
      assertThat(service<RemoteApiProviderService>().listRegisteredApis()).contains(LazyTestApi::class.java.name)

      val api = LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<LazyTestApi>())
      assertThat(api).isSameAs(LazyTestApiImpl)
      assertThat(LazyTestApiProvider.instances.get()).isEqualTo(1)

      LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<LazyTestApi>())
      assertThat(LazyTestApiProvider.instances.get()).describedAs("a second call reuses the provider").isEqualTo(1)
    }
    finally {
      unload(plugin)
    }
    assertThat(LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<LazyTestApi>())).isNull()
    assertThat(service<RemoteApiProviderService>().listRegisteredApis()).doesNotContain(LazyTestApi::class.java.name)
  }

  @Test
  @Timeout(60)
  fun `a declaration that disagrees with the provider is an error, and the provider still works`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadProvider(PartiallyDeclaredApiProvider::class.java, LazyTestApi::class.java.name)
    try {
      val error = LoggedErrorProcessor.executeAndReturnLoggedError {
        assertThat(LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<LazyTestApi>())).isSameAs(LazyTestApiImpl)
      }
      assertThat(error.message).contains(PartiallyDeclaredApiProvider::class.java.name, SecondLazyTestApi::class.java.name)
      assertThat(LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<SecondLazyTestApi>())).isSameAs(SecondLazyTestApiImpl)
      assertThat(PartiallyDeclaredApiProvider.instances.get()).isEqualTo(1)
    }
    finally {
      unload(plugin)
    }
  }
}

@Rpc
interface LazyTestApi : RemoteApi<Unit> {
  suspend fun ping(): Int
}

@Rpc
interface SecondLazyTestApi : RemoteApi<Unit> {
  suspend fun pong(): Int
}

private object LazyTestApiImpl : LazyTestApi {
  override suspend fun ping(): Int = 42
}

private object SecondLazyTestApiImpl : SecondLazyTestApi {
  override suspend fun pong(): Int = 43
}

internal class LazyTestApiProvider : RemoteApiProvider {
  companion object {
    val instances: AtomicInteger = AtomicInteger()
  }

  init {
    instances.incrementAndGet()
  }

  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<LazyTestApi>()) { LazyTestApiImpl }
  }
}

/** Registers two APIs while the test declares one. */
internal class PartiallyDeclaredApiProvider : RemoteApiProvider {
  companion object {
    val instances: AtomicInteger = AtomicInteger()
  }

  init {
    instances.incrementAndGet()
  }

  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<LazyTestApi>()) { LazyTestApiImpl }
    remoteApi(remoteApiDescriptor<SecondLazyTestApi>()) { SecondLazyTestApiImpl }
  }
}
