// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.jetbrains.intellij.build.dev.customCommandSystemProperties
import org.junit.jupiter.api.Test

/** `DevMainImpl` and `PreBuiltDevMain` read the properties of a custom command through this one helper. */
class CustomCommandSystemPropertiesTest {
  @Test
  fun `only the -D arguments become properties, in order`() {
    val properties = customCommandSystemProperties(listOf("-Xmx2g", "-Dide.light=true", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-Dmain=com.example.Main"))

    assertThat(properties).containsExactly(entry("ide.light", "true"), entry("main", "com.example.Main"))
  }

  @Test
  fun `a property without a value is empty and a later value wins`() {
    val properties = customCommandSystemProperties(listOf("-Dflag", "-Dmode=a", "-Dmode=b=c"))

    assertThat(properties).containsExactly(entry("flag", ""), entry("mode", "b=c"))
  }

  @Test
  fun `an unresolved macro fails and names the argument`() {
    assertThatThrownBy { customCommandSystemProperties(listOf("-Didea.home=\$IDE_HOME/lib")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("idea.home=\$IDE_HOME/lib")
  }
}
