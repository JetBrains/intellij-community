// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.intellij.build.impl.BazelBuildInputs
import org.jetbrains.intellij.build.impl.checkProducedPluginDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Checks the consumer of a produced descriptor and its version and compatibility guards.
 */
class DevDistPluginDescriptorTest {
  /**
   * The seam a fragment reads the produced descriptor through, and the guard on what it read.
   *
   * A fragment that reads a produced descriptor cannot compare it against its own patch. The guard checks the stamps
   * instead: the version and the compatibility range must be this assembly's. The two refusals below are its negative
   * control, and this accepted arm is the reference.
   */
  @Test
  fun `a produced descriptor whose stamps agree is accepted`() {
    checkProducedPluginDescriptor(
      mainModule = MAIN_MODULE,
      content = PRODUCED_DESCRIPTOR,
      pluginVersion = "263.99999999.0",
      compatibleSinceUntil = "263.SNAPSHOT" to "263.SNAPSHOT",
    )
  }

  @Test
  fun `a produced descriptor whose version is not this assembly's is refused`() {
    assertThatThrownBy {
      checkProducedPluginDescriptor(
        mainModule = MAIN_MODULE,
        content = PRODUCED_DESCRIPTOR,
        pluginVersion = "263.99999998.0",
        compatibleSinceUntil = "263.SNAPSHOT" to "263.SNAPSHOT",
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(MAIN_MODULE)
      .hasMessageContaining("263.99999998.0")
  }

  @Test
  fun `a produced descriptor whose compatibility range is not this assembly's is refused`() {
    assertThatThrownBy {
      checkProducedPluginDescriptor(
        mainModule = MAIN_MODULE,
        content = PRODUCED_DESCRIPTOR,
        pluginVersion = "263.99999999.0",
        compatibleSinceUntil = "263.1" to "263.*",
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("since-build='263.1'")
  }

  /**
   * With no input manifest there is no declaration to read, so every plugin takes the computed path.
   *
   * Required and not cosmetic: the key is not a Bazel label, so the runfiles fallback the other probe takes would throw
   * on it. The in-process dev assembly runs with no manifest.
   */
  @Test
  fun `no produced descriptor is found without an input manifest`() {
    assertThat(BazelBuildInputs.producedPluginDescriptorIfDeclared(MAIN_MODULE)).isNull()
  }
}

private const val MAIN_MODULE = "intellij.example"
private const val BACKEND = "intellij.example.backend"
private const val FRONTEND = "intellij.example.frontend"

/** A produced descriptor whose stamps are the fixture assembly's: the version and the compatibility range. */
private val PRODUCED_DESCRIPTOR = """
  <idea-plugin>
    <id>com.example</id>
    <version>263.99999999.0</version>
    <idea-version since-build="263.SNAPSHOT" until-build="263.SNAPSHOT" />
    <content>
      <module name="$BACKEND"><![CDATA[<idea-plugin package="$BACKEND" />]]></module>
      <module name="$FRONTEND"><![CDATA[<idea-plugin package="$FRONTEND" />]]></module>
    </content>
  </idea-plugin>
""".trimIndent()
