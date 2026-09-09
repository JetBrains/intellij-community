package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Timeout(30)
class ProductModulesLayoutTest {
  @Test
  fun `assignment defers initialization and caches the list`() {
    val productLayout = ProductModulesLayout()
    val layouts = persistentListOf(PluginLayout.pluginAuto(listOf("test.plugin")))
    var initializations = 0
    productLayout.pluginLayouts = lazy {
      initializations++
      layouts
    }

    assertThat(productLayout.pluginLayouts.isInitialized()).isFalse()
    assertThat(initializations).isZero()
    assertThat(productLayout.pluginLayouts.value).isSameAs(layouts)
    assertThat(productLayout.pluginLayouts.value).isSameAs(layouts)
    assertThat(productLayout.pluginLayouts.isInitialized()).isTrue()
    assertThat(initializations).isEqualTo(1)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `replacement does not evaluate the previous producer`(initializePrevious: Boolean) {
    val productLayout = ProductModulesLayout()
    var previousInitializations = 0
    productLayout.pluginLayouts = lazy {
      previousInitializations++
      persistentListOf(PluginLayout.pluginAuto(listOf("previous.plugin")))
    }
    val previousProducer = productLayout.pluginLayouts
    if (initializePrevious) {
      previousProducer.value
    }
    val replacement = persistentListOf(PluginLayout.pluginAuto(listOf("replacement.plugin")))
    productLayout.pluginLayouts = lazyOf(replacement)

    assertThat(productLayout.pluginLayouts.isInitialized()).isFalse()
    assertThat(productLayout.pluginLayouts.value).isSameAs(replacement)
    assertThat(previousProducer.isInitialized()).isEqualTo(initializePrevious)
    assertThat(previousInitializations).isEqualTo(if (initializePrevious) 1 else 0)
  }

  @Test
  fun `composed producers remain lazy and preserve order`() {
    val productLayout = ProductModulesLayout()
    val firstPlugin = PluginLayout.pluginAuto(listOf("first.plugin"))
    val secondPlugin = PluginLayout.pluginAuto(listOf("second.plugin"))
    val thirdPlugin = PluginLayout.pluginAuto(listOf("third.plugin"))
    productLayout.pluginLayouts = lazy { persistentListOf(firstPlugin, secondPlugin) }
    val originalProducer = productLayout.pluginLayouts
    productLayout.pluginLayouts = lazy { originalProducer.value.adding(thirdPlugin) }
    val extendedProducer = productLayout.pluginLayouts
    productLayout.pluginLayouts = lazy { extendedProducer.value.removing(secondPlugin) }

    assertThat(originalProducer.isInitialized()).isFalse()
    assertThat(extendedProducer.isInitialized()).isFalse()
    assertThat(productLayout.pluginLayouts.isInitialized()).isFalse()
    assertThat(productLayout.pluginLayouts.value).containsExactly(firstPlugin, thirdPlugin)
    assertThat(originalProducer.value).containsExactly(firstPlugin, secondPlugin)
    assertThat(extendedProducer.value).containsExactly(firstPlugin, secondPlugin, thirdPlugin)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `duplicates fail on the first read`(restricted: Boolean) {
    val productLayout = ProductModulesLayout()
    val layouts = persistentListOf(plugin(restricted), plugin(restricted))
    productLayout.pluginLayouts = lazyOf(layouts)

    assertThat(productLayout.pluginLayouts.isInitialized()).isFalse()
    val restrictions = if (restricted) ", bundlingRestrictions=${layouts.first().bundlingRestrictions}" else ""
    assertThatThrownBy { productLayout.pluginLayouts.value }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("PluginLayout(mainModule=test.plugin$restrictions) is duplicated")
    assertThat(productLayout.pluginLayouts.isInitialized()).isFalse()
  }

  @Test
  fun `different restrictions allow variants of the same plugin`() {
    val layouts = persistentListOf(plugin(false), plugin(true))
    val productLayout = ProductModulesLayout()
    productLayout.pluginLayouts = lazyOf(layouts)

    assertThat(productLayout.pluginLayouts.value).isSameAs(layouts)
  }

  @Test
  fun `failed initialization can be retried`() {
    val productLayout = ProductModulesLayout()
    var attempts = 0
    productLayout.pluginLayouts = lazy {
      attempts++
      check(attempts > 1) { "First attempt failed" }
      persistentListOf()
    }

    assertThatThrownBy { productLayout.pluginLayouts.value }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("First attempt failed")
    assertThat(productLayout.pluginLayouts.isInitialized()).isFalse()
    assertThat(productLayout.pluginLayouts.value).isEmpty()
    assertThat(productLayout.pluginLayouts.isInitialized()).isTrue()
    assertThat(attempts).isEqualTo(2)
  }

  @Test
  fun `concurrent first reads initialize once`() {
    val productLayout = ProductModulesLayout()
    val initializations = AtomicInteger()
    val layouts = persistentListOf(PluginLayout.pluginAuto(listOf("test.plugin")))
    productLayout.pluginLayouts = lazy {
      initializations.incrementAndGet()
      layouts
    }
    val ready = CountDownLatch(4)
    val start = CountDownLatch(1)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    try {
      val results = List(4) {
        executor.submit<PersistentList<PluginLayout>> {
          ready.countDown()
          check(start.await(10, TimeUnit.SECONDS))
          productLayout.pluginLayouts.value
        }
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(initializations.get()).isZero()
      start.countDown()

      results.forEach { assertThat(it.get(10, TimeUnit.SECONDS)).isSameAs(layouts) }
      assertThat(initializations.get()).isEqualTo(1)
      assertThat(productLayout.pluginLayouts.value).isSameAs(layouts)
    }
    finally {
      start.countDown()
      executor.shutdownNow()
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
    }
  }

  private fun plugin(restricted: Boolean): PluginLayout {
    return PluginLayout.pluginAuto(listOf("test.plugin")) {
      if (restricted) {
        it.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.LINUX)
      }
    }
  }
}
