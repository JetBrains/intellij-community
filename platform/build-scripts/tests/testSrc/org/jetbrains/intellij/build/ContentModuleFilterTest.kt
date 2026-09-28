// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.JDOMUtil
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.intellij.build.impl.filterAndProcessContentModules
import org.junit.jupiter.api.Test

/**
 * What a refused `<module/>` does to the descriptor. A filter that refuses a module the run time excludes keeps the
 * element of a plugin module and reports it refused; every other refusal removes the element.
 */
class ContentModuleFilterTest {
  @Language("XML")
  private val descriptor = """
    <idea-plugin>
      <content>
        <module name="demo.frontend"/>
        <module name="demo.backend"/>
        <module name="demo.required" loading="required"/>
      </content>
    </idea-plugin>
  """.trimIndent()

  /** Refuses `demo.backend`. [keepsInPlugin] is the answer of [ContentModuleFilter.keepsRefusedModuleInDescriptor] for a plugin. */
  private fun refusingFilter(keepsInPlugin: Boolean): ContentModuleFilter = object : ContentModuleFilter {
    override fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean = moduleName != "demo.backend"

    override fun keepsRefusedModuleInDescriptor(pluginMainModuleName: String?): Boolean = keepsInPlugin && pluginMainModuleName != null
  }

  private fun walk(pluginMainModuleName: String?, filter: ContentModuleFilter): Pair<List<String>, List<Pair<String, Boolean>>> {
    val root = JDOMUtil.load(descriptor)
    val seen = ArrayList<Pair<String, Boolean>>()
    filterAndProcessContentModules(rootElement = root, pluginMainModuleName = pluginMainModuleName, contentModuleFilter = filter) { _, name, _, refused ->
      seen.add(name to refused)
    }
    val left = root.getChildren("content").flatMap { content -> content.getChildren("module").map { it.getAttributeValue("name") } }
    return left to seen
  }

  @Test
  fun `a filter that keeps a refused plugin module leaves its element and reports it refused`() {
    val (left, seen) = walk(pluginMainModuleName = "demo.plugin", filter = refusingFilter(keepsInPlugin = true))

    assertThat(left).containsExactly("demo.frontend", "demo.backend", "demo.required")
    assertThat(seen).containsExactly("demo.frontend" to false, "demo.backend" to true, "demo.required" to false)
  }

  @Test
  fun `a filter that removes a refused plugin module drops its element`() {
    val (left, seen) = walk(pluginMainModuleName = "demo.plugin", filter = refusingFilter(keepsInPlugin = false))

    assertThat(left).containsExactly("demo.frontend", "demo.required")
    assertThat(seen).containsExactly("demo.frontend" to false, "demo.required" to false)
  }

  @Test
  fun `a refused module of the core plugin always leaves`() {
    val (left, seen) = walk(pluginMainModuleName = null, filter = refusingFilter(keepsInPlugin = true))

    assertThat(left).containsExactly("demo.frontend", "demo.required")
    assertThat(seen.map { it.first }).containsExactly("demo.frontend", "demo.required")
  }

  @Test
  fun `a required module is never asked`() {
    val asked = ArrayList<String>()
    val filter = object : ContentModuleFilter {
      override fun isOptionalModuleIncluded(moduleName: String, pluginMainModuleName: String?): Boolean {
        asked.add(moduleName)
        return false
      }

      override fun keepsRefusedModuleInDescriptor(pluginMainModuleName: String?): Boolean = true
    }

    val (left, _) = walk(pluginMainModuleName = "demo.plugin", filter = filter)

    assertThat(asked).containsExactly("demo.frontend", "demo.backend")
    assertThat(left).containsExactly("demo.frontend", "demo.backend", "demo.required")
  }
}
