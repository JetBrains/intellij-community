// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import org.assertj.core.api.Assertions
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Unit tests for [applyLoadingModeFixes] in PluginContentStructureValidator.kt.
 * Tests the regex-based XML modification logic for fixing loading mode violations.
 */
@ExtendWith(TestFailureLogger::class)
class StructuralViolationFixTest {
  @Nested
  inner class ApplyLoadingModeFixesTest {
    @Test
    fun `empty modules set returns content unchanged`() {
      val content = """<module name="test.module" loading="optional"/>"""

      val result = applyLoadingModeFixes(content, emptySet())

      Assertions.assertThat(result).isEqualTo(content)
    }

    @Test
    fun `replaces existing loading=optional with loading=required`() {
      val content = """<module name="test.module" loading="optional"/>"""

      val result = applyLoadingModeFixes(content, setOf("test.module"))

      Assertions.assertThat(result).isEqualTo("""<module name="test.module" loading="required"/>""")
    }

    @Test
    fun `replaces existing loading=on_demand with loading=required`() {
      val content = """<module name="test.module" loading="on_demand"/>"""

      val result = applyLoadingModeFixes(content, setOf("test.module"))

      Assertions.assertThat(result).isEqualTo("""<module name="test.module" loading="required"/>""")
    }

    @Test
    fun `adds loading=required when no loading attribute exists - self-closing tag`() {
      val content = """<module name="test.module"/>"""

      val result = applyLoadingModeFixes(content, setOf("test.module"))

      Assertions.assertThat(result).isEqualTo("""<module name="test.module" loading="required"/>""")
    }

    @Test
    fun `adds loading=required when no loading attribute exists - regular tag`() {
      val content = """<module name="test.module">"""

      val result = applyLoadingModeFixes(content, setOf("test.module"))

      Assertions.assertThat(result).isEqualTo("""<module name="test.module" loading="required">""")
    }

    @Test
    fun `handles whitespace before closing tag - self-closing`() {
      val content = """<module name="test.module" />"""

      val result = applyLoadingModeFixes(content, setOf("test.module"))

      Assertions.assertThat(result).isEqualTo("""<module name="test.module" loading="required" />""")
    }

    @Test
    fun `handles whitespace in loading attribute replacement`() {
      val content = """<module name="test.module" loading="optional" />"""

      val result = applyLoadingModeFixes(content, setOf("test.module"))

      Assertions.assertThat(result).isEqualTo("""<module name="test.module" loading="required" />""")
    }

    @Test
    fun `fixes multiple modules in same content`() {
      val content = """
        <content>
          <module name="module.a" loading="optional"/>
          <module name="module.b"/>
          <module name="module.c" loading="on_demand"/>
        </content>
      """.trimIndent()

      val result = applyLoadingModeFixes(content, setOf("module.a", "module.b", "module.c"))

      Assertions.assertThat(result).isEqualTo("""
        <content>
          <module name="module.a" loading="required"/>
          <module name="module.b" loading="required"/>
          <module name="module.c" loading="required"/>
        </content>
      """.trimIndent())
    }

    @Test
    fun `does not modify modules not in fix set`() {
      val content = """
        <content>
          <module name="module.to.fix" loading="optional"/>
          <module name="module.to.keep" loading="optional"/>
        </content>
      """.trimIndent()

      val result = applyLoadingModeFixes(content, setOf("module.to.fix"))

      Assertions.assertThat(result).isEqualTo("""
        <content>
          <module name="module.to.fix" loading="required"/>
          <module name="module.to.keep" loading="optional"/>
        </content>
      """.trimIndent())
    }

    @Test
    fun `handles module names with dots correctly`() {
      val content = """<module name="com.intellij.platform.util.impl" loading="optional"/>"""

      val result = applyLoadingModeFixes(content, setOf("com.intellij.platform.util.impl"))

      Assertions.assertThat(result).isEqualTo("""<module name="com.intellij.platform.util.impl" loading="required"/>""")
    }

    @Test
    fun `handles module names with special regex characters`() {
      // Module names shouldn't have these, but test regex escaping works
      val content = """<module name="module[test]" loading="optional"/>"""

      val result = applyLoadingModeFixes(content, setOf("module[test]"))

      Assertions.assertThat(result).isEqualTo("""<module name="module[test]" loading="required"/>""")
    }

    @Test
    fun `preserves surrounding content and formatting`() {
      val content = """<?xml version="1.0" encoding="UTF-8"?>
<idea-plugin>
  <content>
    <!-- This is a comment -->
    <module name="test.module" loading="optional"/>
    <module name="other.module"/>
  </content>
</idea-plugin>"""

      val result = applyLoadingModeFixes(content, setOf("test.module"))

      Assertions.assertThat(result).isEqualTo("""<?xml version="1.0" encoding="UTF-8"?>
<idea-plugin>
  <content>
    <!-- This is a comment -->
    <module name="test.module" loading="required"/>
    <module name="other.module"/>
  </content>
</idea-plugin>""")
    }

    @Test
    fun `module not found in content returns content unchanged`() {
      val content = """<module name="existing.module" loading="optional"/>"""

      val result = applyLoadingModeFixes(content, setOf("nonexistent.module"))

      Assertions.assertThat(result).isEqualTo(content)
    }

    @Test
    fun `handles loading=required - leaves unchanged`() {
      val content = """<module name="test.module" loading="required"/>"""

      val result = applyLoadingModeFixes(content, setOf("test.module"))

      // Already required, pattern matches and replaces with same value
      Assertions.assertThat(result).isEqualTo("""<module name="test.module" loading="required"/>""")
    }
  }
}
