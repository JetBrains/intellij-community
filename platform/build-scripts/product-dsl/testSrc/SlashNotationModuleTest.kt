// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.baseModuleName
import com.intellij.platform.pluginGraph.isSlashNotation
import com.intellij.platform.pluginGraph.parentPluginName
import com.intellij.platform.pluginGraph.toDescriptorFileName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Tests for slash-notation module support.
 *
 * Slash notation (e.g., "intellij.restClient/intelliLang") represents virtual content modules:
 * - No separate JPS module - descriptor is in parent plugin's resource root
 * - Descriptor file naming: parent.subModule.xml (dots, not slashes)
 */
@ExtendWith(TestFailureLogger::class)
class SlashNotationModuleTest {
  @Nested
  inner class IsSlashNotationTest {
    @Test
    fun `slash module is detected`() {
      val module = ContentModuleName("intellij.restClient/intelliLang")
      assertThat(module.isSlashNotation()).isTrue()
    }

    @Test
    fun `regular module is not slash notation`() {
      val module = ContentModuleName("intellij.platform.core")
      assertThat(module.isSlashNotation()).isFalse()
    }

    @Test
    fun `module with dot is not slash notation`() {
      val module = ContentModuleName("intellij.restClient.impl")
      assertThat(module.isSlashNotation()).isFalse()
    }
  }

  @Nested
  inner class ParentPluginNameTest {
    @Test
    fun `extracts parent from slash module`() {
      val module = ContentModuleName("intellij.restClient/intelliLang")
      assertThat(module.parentPluginName()).isEqualTo("intellij.restClient")
    }

    @Test
    fun `returns null for regular module`() {
      val module = ContentModuleName("intellij.platform.core")
      assertThat(module.parentPluginName()).isNull()
    }

    @Test
    fun `handles nested slash notation`() {
      val module = ContentModuleName("parent.plugin/sub/nested")
      assertThat(module.parentPluginName()).isEqualTo("parent.plugin")
    }
  }

  @Nested
  inner class ToDescriptorFileNameTest {
    @Test
    fun `converts slash module to descriptor name`() {
      val module = ContentModuleName("intellij.restClient/intelliLang")
      assertThat(module.toDescriptorFileName()).isEqualTo("intellij.restClient.intelliLang.xml")
    }

    @Test
    fun `regular module uses standard naming`() {
      val module = ContentModuleName("intellij.platform.core")
      assertThat(module.toDescriptorFileName()).isEqualTo("intellij.platform.core.xml")
    }

    @Test
    fun `handles nested slash notation`() {
      val module = ContentModuleName("parent.plugin/sub/nested")
      assertThat(module.toDescriptorFileName()).isEqualTo("parent.plugin.sub.nested.xml")
    }
  }

  @Nested
  inner class BaseModuleNameTest {
    @Test
    fun `slash module returns parent as base`() {
      val module = ContentModuleName("intellij.restClient/intelliLang")
      assertThat(module.baseModuleName().value).isEqualTo("intellij.restClient")
    }

    @Test
    fun `regular module returns itself`() {
      val module = ContentModuleName("intellij.platform.core")
      assertThat(module.baseModuleName().value).isEqualTo("intellij.platform.core")
    }

    @Test
    fun `test descriptor returns base without suffix`() {
      val module = ContentModuleName("intellij.platform.core._test")
      assertThat(module.baseModuleName().value).isEqualTo("intellij.platform.core")
    }

    @Test
    fun `slash module takes precedence over test suffix`() {
      // If both slash and test suffix exist, slash should be handled first
      val module = ContentModuleName("intellij.restClient/intelliLang._test")
      assertThat(module.baseModuleName().value).isEqualTo("intellij.restClient")
    }
  }
}
