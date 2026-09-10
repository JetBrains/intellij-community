// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.lang.reflect.Method

internal class AutoLayoutTest {
  @Test
  fun `embedded content module is packed into own jar by default`() {
    assertThat(computeEmbeddedOutputJarPath("intellij.test.content")).isEqualTo("intellij.test.content.jar")
  }

  @Test
  fun `content module without package needs separate jar without marker`() {
    assertThat(needsSeparateJar("<idea-plugin/>")).isTrue()
  }

  private fun computeEmbeddedOutputJarPath(
    moduleName: String,
    modulesWithCustomPath: Set<String> = emptySet(),
  ): String? {
    @Suppress("UNCHECKED_CAST")
    return computeEmbeddedOutputJarPathMethod.invoke(
      null,
      moduleName,
      modulesWithCustomPath,
    ) as String?
  }

  private fun needsSeparateJar(descriptorText: String): Boolean {
    return needsSeparateJarMethod.invoke(
      null,
      descriptorText.toByteArray(),
      mock(JpsModule::class.java),
      PluginLayout.pluginAuto("intellij.test.plugin") {},
      mock(FrontendModuleFilter::class.java),
      JarPackagerDependencyHelper(mock(ModuleOutputProvider::class.java)),
    ) as Boolean
  }

  companion object {
    private val computeEmbeddedOutputJarPathMethod: Method = Class
      .forName("org.jetbrains.intellij.build.AutoLayoutKt")
      .getDeclaredMethod(
        "computeEmbeddedOutputJarPath",
        String::class.java,
        Set::class.java,
      )
      .also { it.isAccessible = true }

    private val needsSeparateJarMethod: Method = Class
      .forName("org.jetbrains.intellij.build.AutoLayoutKt")
      .getDeclaredMethod(
        "needsSeparateJar",
        ByteArray::class.java,
        JpsModule::class.java,
        PluginLayout::class.java,
        FrontendModuleFilter::class.java,
        JarPackagerDependencyHelper::class.java,
      )
      .also { it.isAccessible = true }
  }
}
