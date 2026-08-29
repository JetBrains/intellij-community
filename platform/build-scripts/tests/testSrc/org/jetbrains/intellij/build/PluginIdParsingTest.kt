// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.JDOMUtil
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.parsePluginId
import org.junit.jupiter.api.Test

/**
 * [parsePluginId] takes out of one plugin descriptor the id that a variant of the plugin set names.
 */
class PluginIdParsingTest {
  @Test
  fun `the descriptor states an id`() {
    assertThat(parse("<idea-plugin><id>com.example.plugin</id></idea-plugin>")).isEqualTo("com.example.plugin")
  }

  @Test
  fun `the name stands in for a missing id`() {
    assertThat(parse("<idea-plugin><name>My Plugin</name></idea-plugin>")).isEqualTo("My Plugin")
  }

  @Test
  fun `the id wins over the name`() {
    assertThat(parse("<idea-plugin><id>com.example.plugin</id><name>My Plugin</name></idea-plugin>"))
      .isEqualTo("com.example.plugin")
  }

  @Test
  fun `a descriptor without an id and a name states no id`() {
    assertThat(parse("<idea-plugin/>")).isNull()
  }

  @Test
  fun `an empty id states no id`() {
    assertThat(parse("<idea-plugin><id></id></idea-plugin>")).isNull()
  }

  private fun parse(descriptor: String): String? = parsePluginId(JDOMUtil.load(descriptor.trimIndent()))
}
