// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The two lists of the platform jar order. The jars before the longest ascending run of keys are `first`. The jars after
 * it and the library-only jars are `last`.
 */
class DevDistPlatformJarOrderTest {
  @Test
  fun `the jars around the sorted range are the two lists`() {
    val order = derivePlatformJarOrder(
      product = "test",
      keys = listOf(
        "nio-fs.jar" to "intellij.platform.core.nio.fs",
        "util_rt.jar" to "intellij.platform.util.rt",
        "util-8.jar" to "intellij.platform.util.jdkEx",
        "fleet.kernel.jar" to "fleet.kernel",
        "app.jar" to "intellij.platform.ide",
        "xml.jar" to "intellij.xml",
        "ext/platform-main.jar" to "intellij.platform.main",
      ),
      libraryOnly = listOf("product-backend.jar"),
    )
    assertThat(order).isEqualTo(PlatformJarOrder(
      first = listOf("nio-fs.jar", "util_rt.jar", "util-8.jar"),
      last = listOf("ext/platform-main.jar", "product-backend.jar"),
    ))
  }

  @Test
  fun `the earliest run wins a tie`() {
    val order = derivePlatformJarOrder(
      product = "test",
      keys = listOf("a.jar" to "a", "b.jar" to "b", "c.jar" to "0", "d.jar" to "1"),
      libraryOnly = emptyList(),
    )
    assertThat(order).isEqualTo(PlatformJarOrder(first = emptyList(), last = listOf("c.jar", "d.jar")))
  }

  @Test
  fun `a platform without a module jar lists its library-only jars last`() {
    val order = derivePlatformJarOrder(product = "test", keys = emptyList(), libraryOnly = listOf("b.jar", "a.jar"))
    assertThat(order).isEqualTo(PlatformJarOrder(first = emptyList(), last = listOf("b.jar", "a.jar")))
  }
}
