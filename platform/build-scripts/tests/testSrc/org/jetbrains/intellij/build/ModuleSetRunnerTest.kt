// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.pluginModelTool.discoverCommunityModuleSetSources
import com.intellij.platform.buildScripts.pluginModelTool.parseGeneratorOptions
import com.intellij.platform.buildScripts.pluginModelTool.parseJsonArgument
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetSourceLabels
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ModuleSetRunnerTest {
  @Test
  fun `community discovery includes the library module sets`() {
    val sources = discoverCommunityModuleSetSources()
    assertThat(sources.keys).containsExactlyInAnyOrder(ModuleSetSourceLabels.COMMUNITY, ModuleSetSourceLabels.CORE, ModuleSetSourceLabels.LIBRARIES)

    val librarySource = sources.getValue(ModuleSetSourceLabels.LIBRARIES)
    assertThat(librarySource.sourceFile).endsWith("/LibraryModuleSets.kt")
    val libraries = librarySource.moduleSets.single { it.name == "libraries.platform" }
    assertThat(libraries.modules.map { it.moduleId.name }).contains("intellij.libraries.guava")

    val moduleSetNames = sources.values.flatMap { source -> source.moduleSets.map { it.name } }
    assertThat(moduleSetNames).doesNotHaveDuplicates()
  }

  @Test
  fun `plain json argument requests full export`() {
    assertThat(parseJsonArgument("--json")).isNull()
  }

  @Test
  fun `inline json argument decodes compact filter`() {
    val filter = parseJsonArgument("""--json={"filter":"summary","limit":3}""")

    assertThat(filter?.filter).isEqualTo("summary")
    assertThat(filter?.limit).isEqualTo(3)
  }

  @Test
  fun `stdin json argument reads payload from stdin reader`() {
    val filter = parseJsonArgument(
      arg = "--json=-",
      stdinReader = { """{"filter":"moduleInfo","module":"intellij.platform.vcs.impl"}""" },
      fileReader = { error("file reader should not be called") },
    )

    assertThat(filter?.filter).isEqualTo("moduleInfo")
    assertThat(filter?.module).isEqualTo("intellij.platform.vcs.impl")
  }

  @Test
  fun `file json argument reads payload from referenced file`() {
    val filter = parseJsonArgument(
      arg = "--json=@query.json",
      stdinReader = { error("stdin reader should not be called") },
      fileReader = {
        assertThat(it).isEqualTo(Path.of("query.json"))
        """{"filter":"productCompare","product":"IDEA","product2":"IC"}"""
      },
    )

    assertThat(filter?.filter).isEqualTo("productCompare")
    assertThat(filter?.product).isEqualTo("IDEA")
    assertThat(filter?.product2).isEqualTo("IC")
  }

  @Test
  fun `invalid json argument throws`() {
    assertThatThrownBy { parseJsonArgument("--json={not json}") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Failed to parse JSON filter")
  }

  @Test
  fun `json argument ignores unknown keys`() {
    val filter = parseJsonArgument("""--json={"filter":"summary","operation":"old-mcp-shape","ignored":true}""")

    assertThat(filter?.filter).isEqualTo("summary")
  }

  @Test
  fun `a run without the trace argument writes no trace`() {
    assertThat(parseGeneratorOptions(arrayOf("--check")).traceFile).isNull()
  }

  @Test
  fun `the trace argument becomes an absolute path`() {
    val options = parseGeneratorOptions(arrayOf("--check", "--trace=trace.json"))

    assertThat(options.traceFile).isAbsolute()
    assertThat(options.traceFile?.fileName).isEqualTo(Path.of("trace.json"))
    assertThat(options.commitChanges).isFalse()
  }

  @Test
  fun `an empty trace argument throws`() {
    assertThatThrownBy { parseGeneratorOptions(arrayOf("--trace=")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("--trace needs a file")
  }
}
