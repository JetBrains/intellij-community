// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.lang.reflect.Method

internal class AutoLayoutTest {
  @Test
  fun `embedded content module is packed into own jar by default`() {
    assertThat(computeEmbeddedOutputJarPath("intellij.test.content")).isEqualTo("intellij.test.content.jar")
  }

  @Test
  fun `embedded content module with marker is packed into plugin jar`() {
    assertThat(computeEmbeddedOutputJarPath("intellij.test.content", packIntoPluginJar = true)).isEqualTo("test-plugin.jar")
  }

  @Test
  fun `embedded frontend content module with marker is packed into plugin frontend jar`() {
    val frontendModuleFilter = mock(FrontendModuleFilter::class.java)
    `when`(frontendModuleFilter.isModuleCompatibleWithFrontend("intellij.test.content")).thenReturn(true)

    assertThat(computeEmbeddedOutputJarPath(
      moduleName = "intellij.test.content",
      frontendModuleFilter = frontendModuleFilter,
      packIntoPluginJar = true,
    )).isEqualTo("test-plugin-frontend.jar")
  }

  @Test
  fun `explicit custom path still takes precedence for embedded content module`() {
    assertThat(computeEmbeddedOutputJarPath(
      moduleName = "intellij.test.content",
      modulesWithCustomPath = setOf("intellij.test.content"),
      packIntoPluginJar = true,
    )).isNull()
  }

  @Test
  fun `descriptor marker is recognized`() {
    assertThat(hasPackContentIntoPluginJarMarker(
      """
        <!-- intellij-build: pack-content-into-plugin-jar -->
        <idea-plugin/>
      """.trimIndent()
    )).isTrue()
  }

  @Test
  fun `missing descriptor data is treated as unmarked`() {
    assertThat(hasPackContentIntoPluginJarMarker(null)).isFalse()
  }

  @Test
  fun `descriptor marker disables separate jar for content module without package`() {
    assertThat(needsSeparateJar(
      """
        <!-- intellij-build: pack-content-into-plugin-jar -->
        <idea-plugin/>
      """.trimIndent()
    )).isFalse()
  }

  @Test
  fun `content module without package needs separate jar without marker`() {
    assertThat(needsSeparateJar("<idea-plugin/>")).isTrue()
  }

  private fun computeEmbeddedOutputJarPath(
    moduleName: String,
    modulesWithCustomPath: Set<String> = emptySet(),
    frontendModuleFilter: FrontendModuleFilter = mock(FrontendModuleFilter::class.java),
    packIntoPluginJar: Boolean = false,
  ): String? {
    @Suppress("UNCHECKED_CAST")
    return computeEmbeddedOutputJarPathMethod.invoke(
      null,
      moduleName,
      modulesWithCustomPath,
      PluginLayout.pluginAuto("intellij.test.plugin") {},
      frontendModuleFilter,
      packIntoPluginJar,
    ) as String?
  }

  private fun hasPackContentIntoPluginJarMarker(descriptorText: String?): Boolean {
    return hasPackContentIntoPluginJarMarkerMethod.invoke(null, descriptorText?.toByteArray()) as Boolean
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
        PluginLayout::class.java,
        FrontendModuleFilter::class.java,
        Boolean::class.javaPrimitiveType!!,
      )
      .also { it.isAccessible = true }

    private val hasPackContentIntoPluginJarMarkerMethod: Method = Class
      .forName("org.jetbrains.intellij.build.AutoLayoutKt")
      .getDeclaredMethod("hasPackContentIntoPluginJarMarker", ByteArray::class.java)
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
