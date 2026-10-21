// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.tests

import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointDeferredListenersNotification
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.rpc.backend.RemoteApiProviderBean
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
import com.intellij.util.xml.dom.XmlElement
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
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
    CancelledOnceApiProvider.runs.set(0)
    CancelledBetweenApisProvider.runs.set(0)
    CancelledConstructionApiProvider.instances.set(0)
    FailingApiProvider.runs.set(0)
  }

  private suspend fun loadProvider(providerClass: Class<*>, apiInterfaces: String): Disposable {
    return loadExtension(
      """<platform.rpc.backend.remoteApiProvider implementation="${providerClass.name}" apiInterfaces="$apiInterfaces"/>"""
    )
  }

  private suspend fun loadRegistration(apiInterface: Class<*>, implementationClass: Class<*>): Disposable {
    return loadExtension(
      """<platform.rpc.backend.remoteApi apiInterface="${apiInterface.name}" implementationClass="${implementationClass.name}"/>"""
    )
  }

  /** A dynamic plugin load is a modal operation, so it runs on the EDT. */
  private suspend fun loadExtension(extensionXml: String): Disposable = withContext(Dispatchers.EDT) {
    loadPluginWithText(
      pluginSpec = plugin {
        dependsIntellijModulesLang()
        extensions(extensionXml)
      },
      pluginsDir = tempDir.resolve("plugins"),
    )
  }

  /** Runs [action] with a cancelled job in the thread context, as a call from a cancelled coroutine does. */
  private fun <T> inCancelledContext(action: () -> T): T {
    return installThreadContext(Job().apply { cancel() }, replace = true, action)
  }

  private suspend fun unload(plugin: Disposable) {
    withContext(Dispatchers.EDT) {
      Disposer.dispose(plugin)
    }
  }

  /**
   * Registers [LazyTestApiProvider] the way a dynamic plugin load does: the extension is visible at once,
   * the change listeners are notified later. Runs [block] in that window, then notifies and unregisters.
   */
  private fun withDeferredProviderRegistration(apiInterfaces: String?, block: () -> Unit) {
    val point = RemoteApiProvider.EP_NAME.point as ExtensionPointImpl<RemoteApiProviderBean>
    val attributes = LinkedHashMap<String, String>()
    attributes["implementation"] = LazyTestApiProvider::class.java.name
    apiInterfaces?.let { attributes["apiInterfaces"] = it }
    val descriptor = ExtensionDescriptor(
      implementation = LazyTestApiProvider::class.java.name,
      os = null,
      orderId = null,
      order = LoadingOrder.ANY,
      element = XmlElement("remoteApiProvider", attributes),
      hasExtraAttributes = apiInterfaces != null,
    )
    val pluginDescriptor = DefaultPluginDescriptor(PluginId.getId("RemoteApiRegistryLazyTest"), LazyTestApiProvider::class.java.classLoader)
    val deferred = ArrayList<ExtensionPointDeferredListenersNotification>()
    point.registerExtensions(listOf(descriptor), pluginDescriptor, deferred)
    try {
      block()
    }
    finally {
      deferred.forEach { it.notify.run() }
      point.unregisterExtensions({ _, adapter -> adapter.pluginDescriptor !== pluginDescriptor }, false)
    }
  }

  @Test
  @Timeout(60)
  fun `provider is constructed on the first call, and released with the plugin`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadProvider(LazyTestApiProvider::class.java, LazyTestApi::class.java.name)
    try {
      assertThat(LazyTestApiProvider.instances.get()).describedAs("provider instances after plugin load").isZero()
      assertThat(service<RemoteApiProviderService>().listRegisteredApis()).contains(LazyTestApi::class.java.name)
      assertThat(LiteRemoteApiProviderService.isConnected()).isTrue()
      assertThat(LazyTestApiProvider.instances.get()).describedAs("provider instances after registry initialization").isZero()

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
      service<RemoteApiProviderService>()
      assertThat(LiteRemoteApiProviderService.isConnected()).isTrue()
      assertThat(PartiallyDeclaredApiProvider.instances.get()).isZero()
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

  @Test
  @Timeout(60)
  fun `a declared API is resolved before the change listener rebuilds the index`() {
    withDeferredProviderRegistration(apiInterfaces = LazyTestApi::class.java.name) {
      assertThat(LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<LazyTestApi>())).isSameAs(LazyTestApiImpl)
    }
  }

  @Test
  @Timeout(60)
  fun `an undeclared API is resolved before the change listener rebuilds the index`() {
    withDeferredProviderRegistration(apiInterfaces = null) {
      assertThat(LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<LazyTestApi>())).isSameAs(LazyTestApiImpl)
    }
  }

  /** IJPL-258600. */
  @Test
  @Timeout(60)
  fun `a provider cancelled before its first API runs again on the next call`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadProvider(CancelledOnceApiProvider::class.java, LazyTestApi::class.java.name)
    try {
      val registry = service<RemoteApiProviderService>()
      assertThatThrownBy { registry.tryResolve(remoteApiDescriptor<LazyTestApi>()) }.isInstanceOf(CancellationException::class.java)
      assertThat(registry.tryResolve(remoteApiDescriptor<LazyTestApi>())).isSameAs(LazyTestApiImpl)
      assertThat(CancelledOnceApiProvider.runs.get()).isEqualTo(2)
    }
    finally {
      unload(plugin)
    }
  }

  /** IJPL-258600. */
  @Test
  @Timeout(60)
  fun `a provider cancelled between two APIs keeps neither, and runs again on the next call`(): Unit = timeoutRunBlocking(30.seconds) {
    val apiInterfaces = "${LazyTestApi::class.java.name},${SecondLazyTestApi::class.java.name}"
    val plugin = loadProvider(CancelledBetweenApisProvider::class.java, apiInterfaces)
    try {
      val registry = service<RemoteApiProviderService>()
      assertThatThrownBy { registry.tryResolve(remoteApiDescriptor<SecondLazyTestApi>()) }.isInstanceOf(CancellationException::class.java)
      assertThat(registry.tryResolve(remoteApiDescriptor<LazyTestApi>())).isSameAs(LazyTestApiImpl)
      assertThat(CancelledBetweenApisProvider.runs.get()).describedAs("the registered API of the cancelled run is not kept").isEqualTo(2)
      assertThat(registry.tryResolve(remoteApiDescriptor<SecondLazyTestApi>())).isSameAs(SecondLazyTestApiImpl)
      assertThat(CancelledBetweenApisProvider.runs.get()).isEqualTo(2)
    }
    finally {
      unload(plugin)
    }
  }

  /** IJPL-258600. As in the Air provider, the cancellation comes from a service request in the `implementation()` of an API. */
  @Test
  @Timeout(60)
  fun `a provider cancelled in a service request of an API runs again on the next call`(): Unit = timeoutRunBlocking(30.seconds) {
    val apiInterfaces = "${LazyTestApi::class.java.name},${SecondLazyTestApi::class.java.name}"
    val plugin = loadProvider(ServiceBackedApiProvider::class.java, apiInterfaces)
    try {
      val registry = service<RemoteApiProviderService>()
      assertThatThrownBy { inCancelledContext { registry.tryResolve(remoteApiDescriptor<SecondLazyTestApi>()) } }
        .isInstanceOf(CancellationException::class.java)
      assertThat(registry.tryResolve(remoteApiDescriptor<SecondLazyTestApi>())).isSameAs(SecondLazyTestApiImpl)
      assertThat(registry.tryResolve(remoteApiDescriptor<LazyTestApi>())).isSameAs(LazyTestApiImpl)
    }
    finally {
      unload(plugin)
    }
  }

  /** IJPL-258600. */
  @Test
  @Timeout(60)
  fun `a provider cancelled in its constructor is constructed again on the next call`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadProvider(CancelledConstructionApiProvider::class.java, LazyTestApi::class.java.name)
    try {
      val registry = service<RemoteApiProviderService>()
      assertThatThrownBy { registry.tryResolve(remoteApiDescriptor<LazyTestApi>()) }.isInstanceOf(ProcessCanceledException::class.java)
      assertThat(registry.tryResolve(remoteApiDescriptor<LazyTestApi>())).isSameAs(LazyTestApiImpl)
      assertThat(CancelledConstructionApiProvider.instances.get()).isEqualTo(2)
    }
    finally {
      unload(plugin)
    }
  }

  /** IJPL-258600. */
  @Test
  @Timeout(60)
  fun `a bean registration created in a cancelled context is created again on the next call`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadRegistration(LazyTestApi::class.java, RegisteredLazyTestApiImpl::class.java)
    try {
      val registry = service<RemoteApiProviderService>()
      assertThatThrownBy { inCancelledContext { registry.tryResolve(remoteApiDescriptor<LazyTestApi>()) } }
        .isInstanceOf(ProcessCanceledException::class.java)
      assertThat(registry.tryResolve(remoteApiDescriptor<LazyTestApi>())).isInstanceOf(RegisteredLazyTestApiImpl::class.java)
    }
    finally {
      unload(plugin)
    }
  }

  @Test
  @Timeout(60)
  fun `a failing provider is logged once and does not run again`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadProvider(FailingApiProvider::class.java, LazyTestApi::class.java.name)
    try {
      val registry = service<RemoteApiProviderService>()
      val error = LoggedErrorProcessor.executeAndReturnLoggedError {
        assertThat(registry.tryResolve(remoteApiDescriptor<LazyTestApi>())).isNull()
      }
      assertThat(error.message).contains(FailingApiProvider::class.java.name)
      assertThat(registry.tryResolve(remoteApiDescriptor<LazyTestApi>())).isNull()
      assertThat(FailingApiProvider.runs.get()).isEqualTo(1)
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

/** The implementation of a `platform.rpc.backend.remoteApi` bean registration. */
internal class RegisteredLazyTestApiImpl : LazyTestApi {
  override suspend fun ping(): Int = 44
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

/** Throws a cancellation from the first `remoteApis()` call, before it registers an API. */
internal class CancelledOnceApiProvider : RemoteApiProvider {
  companion object {
    val runs: AtomicInteger = AtomicInteger()
  }

  override fun RemoteApiProvider.Sink.remoteApis() {
    if (runs.incrementAndGet() == 1) {
      throw CancellationException("the first run is cancelled")
    }
    remoteApi(remoteApiDescriptor<LazyTestApi>()) { LazyTestApiImpl }
  }
}

/** Registers [LazyTestApi]. In the first `remoteApis()` call, throws a cancellation before it registers [SecondLazyTestApi]. */
internal class CancelledBetweenApisProvider : RemoteApiProvider {
  companion object {
    val runs: AtomicInteger = AtomicInteger()
  }

  override fun RemoteApiProvider.Sink.remoteApis() {
    val run = runs.incrementAndGet()
    remoteApi(remoteApiDescriptor<LazyTestApi>()) { LazyTestApiImpl }
    if (run == 1) {
      throw CancellationException("the first run is cancelled")
    }
    remoteApi(remoteApiDescriptor<SecondLazyTestApi>()) { SecondLazyTestApiImpl }
  }
}

/**
 * Throws a cancellation from the first construction.
 * It is a [ProcessCanceledException], because the extension framework logs another exception from a constructor as an error.
 */
internal class CancelledConstructionApiProvider : RemoteApiProvider {
  companion object {
    val instances: AtomicInteger = AtomicInteger()
  }

  init {
    if (instances.incrementAndGet() == 1) {
      throw ProcessCanceledException()
    }
  }

  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<LazyTestApi>()) { LazyTestApiImpl }
  }
}

/** Throws an error from every `remoteApis()` call. */
internal class FailingApiProvider : RemoteApiProvider {
  companion object {
    val runs: AtomicInteger = AtomicInteger()
  }

  override fun RemoteApiProvider.Sink.remoteApis() {
    runs.incrementAndGet()
    throw IllegalStateException("the provider fails")
  }
}

/** Requested only by [ServiceBackedApiProvider], so the first request creates the service. */
@Service
internal class LazyTestApiService

/** The implementation of [LazyTestApi] requests a service, as the Air `ThreadGuiRemoteApiProvider` does. */
internal class ServiceBackedApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<LazyTestApi>()) {
      service<LazyTestApiService>()
      LazyTestApiImpl
    }
    remoteApi(remoteApiDescriptor<SecondLazyTestApi>()) { SecondLazyTestApiImpl }
  }
}
