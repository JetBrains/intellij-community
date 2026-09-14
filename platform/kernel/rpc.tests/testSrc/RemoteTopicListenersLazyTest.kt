// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.tests

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.rpc.topics.ApplicationRemoteTopic
import com.intellij.platform.rpc.topics.ApplicationRemoteTopicListener
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.platform.rpc.topics.broadcast
import com.intellij.platform.rpc.topics.impl.RemoteTopicListenerIndex
import com.intellij.platform.testFramework.loadPluginWithText
import com.intellij.platform.testFramework.plugins.dependsIntellijModulesLang
import com.intellij.platform.testFramework.plugins.extensions
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * A remote topic listener declared with `topicId` is constructed on the first event of its topic, not when the plugin loads.
 * IJPL-254356.
 */
@TestApplication
// Synthetic plugins in this test do not contribute index extensions.
@SystemProperty(propertyKey = "intellij.indexes.skip.reload.on.plugin.load.unload", propertyValue = "true")
class RemoteTopicListenersLazyTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val project: Project get() = projectFixture.get()
  private val tempDir by tempPathFixture()

  @TestDisposable
  lateinit var testDisposable: Disposable

  @BeforeEach
  fun resetCounters() {
    LazyAppListener.instances.set(0)
    LazyAppListener.events.clear()
    LazyProjectListener.instances.set(0)
    LazyProjectListener.events.clear()
    MismatchedAppListener.instances.set(0)
  }

  /** A dynamic plugin load is a modal operation, so it runs on the EDT. */
  private suspend fun loadListeners(extensionsXml: String): Disposable = withContext(Dispatchers.EDT) {
    loadPluginWithText(
      pluginSpec = plugin {
        dependsIntellijModulesLang()
        extensions(extensionsXml)
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
  fun `application listener is constructed on the first event of its topic`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadListeners(
      """<platform.rpc.applicationRemoteTopicListener implementation="${LazyAppListener::class.java.name}" topicId="${LAZY_APP_TOPIC.id}"/>"""
    )
    try {
      val index = RemoteTopicListenerIndex.getInstance()
      assertThat(LazyAppListener.instances.get()).describedAs("listener instances after plugin load").isZero()
      assertThat(index.applicationListeners(OTHER_APP_TOPIC.id)).isEmpty()
      assertThat(LazyAppListener.instances.get()).describedAs("listener instances after a lookup of another topic").isZero()

      assertThat(index.applicationListeners(LAZY_APP_TOPIC.id)).hasSize(1).allMatch { it is LazyAppListener }
      assertThat(LazyAppListener.instances.get()).isEqualTo(1)

      val delivered = CompletableDeferred<Unit>()
      LazyAppListener.onEvent = { delivered.complete(Unit) }
      LAZY_APP_TOPIC.broadcast("hello")
      delivered.await()
      assertThat(LazyAppListener.events).contains("hello")
      assertThat(LazyAppListener.instances.get()).isEqualTo(1)
    }
    finally {
      unload(plugin)
    }
    assertThat(RemoteTopicListenerIndex.getInstance().applicationListeners(LAZY_APP_TOPIC.id)).isEmpty()
  }

  @Test
  @Timeout(60)
  fun `project listener is constructed on the first event of its topic`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadListeners(
      """<platform.rpc.projectRemoteTopicListener implementation="${LazyProjectListener::class.java.name}" topicId="${LAZY_PROJECT_TOPIC.id}"/>"""
    )
    try {
      val index = RemoteTopicListenerIndex.getInstance()
      assertThat(LazyProjectListener.instances.get()).isZero()
      assertThat(index.projectListeners(OTHER_APP_TOPIC.id)).isEmpty()
      assertThat(LazyProjectListener.instances.get()).isZero()

      val delivered = CompletableDeferred<Unit>()
      LazyProjectListener.onEvent = { delivered.complete(Unit) }
      LAZY_PROJECT_TOPIC.broadcast(project, 7)
      delivered.await()
      assertThat(LazyProjectListener.events).contains(7)
      assertThat(LazyProjectListener.instances.get()).isEqualTo(1)
    }
    finally {
      unload(plugin)
    }
  }

  @Test
  @Timeout(60)
  fun `a topicId that disagrees with the listener is an error, and the listener receives no event`(): Unit = timeoutRunBlocking(30.seconds) {
    val plugin = loadListeners(
      """<platform.rpc.applicationRemoteTopicListener implementation="${MismatchedAppListener::class.java.name}" topicId="${OTHER_APP_TOPIC.id}"/>"""
    )
    try {
      val index = RemoteTopicListenerIndex.getInstance()
      val error = LoggedErrorProcessor.executeAndReturnLoggedError {
        assertThat(index.applicationListeners(OTHER_APP_TOPIC.id)).isEmpty()
      }
      assertThat(error.message).contains(MismatchedAppListener::class.java.name, OTHER_APP_TOPIC.id, LAZY_APP_TOPIC.id)
      assertThat(MismatchedAppListener.instances.get()).isEqualTo(1)
    }
    finally {
      unload(plugin)
    }
  }

  @Test
  fun `a listener registered without the attribute is found by its topic`() {
    val listener = object : ApplicationRemoteTopicListener<String> {
      override val topic: ApplicationRemoteTopic<String> = OTHER_APP_TOPIC
      override fun handleEvent(event: String) {}
    }
    ApplicationRemoteTopicListener.EP_NAME.point.registerExtension(listener, testDisposable)
    assertThat(RemoteTopicListenerIndex.getInstance().applicationListeners(OTHER_APP_TOPIC.id)).contains(listener)
  }
}

private val LAZY_APP_TOPIC: ApplicationRemoteTopic<String> = ApplicationRemoteTopic("lazy.test.app", String.serializer())
private val OTHER_APP_TOPIC: ApplicationRemoteTopic<String> = ApplicationRemoteTopic("lazy.test.other", String.serializer())
private val LAZY_PROJECT_TOPIC: ProjectRemoteTopic<Int> = ProjectRemoteTopic("lazy.test.project", Int.serializer())

internal class LazyAppListener : ApplicationRemoteTopicListener<String> {
  companion object {
    val instances: AtomicInteger = AtomicInteger()
    val events: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var onEvent: () -> Unit = {}
  }

  init {
    instances.incrementAndGet()
  }

  override val topic: ApplicationRemoteTopic<String> = LAZY_APP_TOPIC

  override fun handleEvent(event: String) {
    events.add(event)
    onEvent()
  }
}

internal class LazyProjectListener : ProjectRemoteTopicListener<Int> {
  companion object {
    val instances: AtomicInteger = AtomicInteger()
    val events: MutableList<Int> = CopyOnWriteArrayList()

    @Volatile
    var onEvent: () -> Unit = {}
  }

  init {
    instances.incrementAndGet()
  }

  override val topic: ProjectRemoteTopic<Int> = LAZY_PROJECT_TOPIC

  override fun handleEvent(project: Project, event: Int) {
    events.add(event)
    onEvent()
  }
}

/** The test declares it with the id of [OTHER_APP_TOPIC]. */
internal class MismatchedAppListener : ApplicationRemoteTopicListener<String> {
  companion object {
    val instances: AtomicInteger = AtomicInteger()
  }

  init {
    instances.incrementAndGet()
  }

  override val topic: ApplicationRemoteTopic<String> = LAZY_APP_TOPIC

  override fun handleEvent(event: String) {}
}
