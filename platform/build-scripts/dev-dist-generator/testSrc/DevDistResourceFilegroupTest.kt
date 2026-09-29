// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.jetbrains.intellij.build.dev.DevPluginResourceExclusions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DevDistResourceFilegroupTest {
  @TempDir
  lateinit var dir: Path

  @Test
  fun `one directory renders inline`() {
    assertThat(renderResourceFilegroup(listOf("resources/shell-integrations"))).isEqualTo(
      """
      filegroup(
          name = "dev_dist_resources",
          srcs = glob(
              ["resources/shell-integrations/**"],
          ),
          visibility = ["//visibility:public"],
      )
      """.trimIndent() + "\n",
    )
  }

  @Test
  fun `several directories render sorted, one per line`() {
    assertThat(renderResourceFilegroup(listOf("rubystubs", "resources/rb", "rubysigs"))).isEqualTo(
      """
      filegroup(
          name = "dev_dist_resources",
          srcs = glob(
              [
                  "resources/rb/**",
                  "rubysigs/**",
                  "rubystubs/**",
              ],
          ),
          visibility = ["//visibility:public"],
      )
      """.trimIndent() + "\n",
    )
  }

  @Test
  fun `a file resource is exported`() {
    assertThat(renderResourceFileExports(listOf("hotswap/gragent.jar"))).isEqualTo(
      """
      exports_files(
          ["hotswap/gragent.jar"],
          visibility = ["//visibility:public"],
      )
      """.trimIndent() + "\n",
    )
    assertThat(renderResourceFileExports(listOf("b.txt", "a.txt"))).isEqualTo(
      """
      exports_files(
          [
              "a.txt",
              "b.txt",
          ],
          visibility = ["//visibility:public"],
      )
      """.trimIndent() + "\n",
    )
  }

  /**
   * The inputs of a simple plugin that copies a `withResource*` directory and a `withResource*` file. Its `dev_plugin`
   * names the package filegroup and the source file, so its package needs both statements, as a complex plugin's does.
   */
  @Test
  fun `a simple plugin that copies resources gets the filegroup and the export of its package`() {
    val statements = DevDistResourceStatements()
    statements.addPlanInputs(
      plugin = "intellij.x",
      inputs = listOf(
        DevDistPluginRawInput(id = "intellij.x", label = "//plugins/x:x", kind = "directory", fileName = "intellij.x"),
        DevDistPluginRawInput(id = "module-resource:0:source", label = "//plugins/x:dev_dist_resources", kind = "directory", fileName = "helpers", sourceTreePrefix = "plugins/x/helpers"),
        DevDistPluginRawInput(id = "module-resource:1:source", label = "//plugins/x:helper.sh", kind = "file", fileName = "helper.sh"),
      ),
    )

    val rendered: Map<String, String> = statements.render(syntheticIndex(dir, "intellij.x" to "//plugins/x:x.jar"))

    assertThat(rendered).containsExactly(entry(
      "intellij.x",
      """
      filegroup(
          name = "dev_dist_resources",
          srcs = glob(
              ["helpers/**"],
          ),
          visibility = ["//visibility:public"],
      )

      exports_files(
          ["helper.sh"],
          visibility = ["//visibility:public"],
      )
      """.trimIndent() + "\n",
    ))
  }

  @Test
  fun `a filtered directory renders its own filegroup with the excludes`() {
    val exclusions = DevPluginResourceExclusions(files = listOf("setup.py"), directories = listOf("tests", "pydev/pydev_test*"))

    assertThat(renderFilteredResourceFilegroup("helpers", exclusions)).isEqualTo(
      """
      filegroup(
          name = "dev_dist_resources_helpers",
          srcs = glob(
              ["helpers/**"],
              exclude = [
                  "helpers/**/pydev/pydev_test*/**/*",
                  "helpers/**/setup.py",
                  "helpers/**/tests/**/*",
              ],
          ),
          visibility = ["//visibility:public"],
      )
      """.trimIndent() + "\n",
    )
    assertThat(renderFilteredResourceFilegroup("lib/data", DevPluginResourceExclusions(files = listOf("x.txt")))).isEqualTo(
      """
      filegroup(
          name = "dev_dist_resources_lib_data",
          srcs = glob(
              ["lib/data/**"],
              exclude = ["lib/data/**/x.txt"],
          ),
          visibility = ["//visibility:public"],
      )
      """.trimIndent() + "\n",
    )
  }

  /**
   * A package that serves a plain directory and a filtered one gets the package filegroup and the filtered filegroup.
   * The filtered directory is not a member of the package filegroup, so its excluded files are no inputs.
   */
  @Test
  fun `a filtered directory gets a filegroup beside the package filegroup`() {
    val statements = DevDistResourceStatements()
    statements.addPlanInputs(
      plugin = "intellij.x",
      inputs = listOf(
        DevDistPluginRawInput(id = "module-resource:0:source", label = "//plugins/x:dev_dist_resources", kind = "directory", fileName = "data", sourceTreePrefix = "plugins/x/data"),
        DevDistPluginRawInput(
          id = "module-resource:1:source",
          label = "//plugins/x:dev_dist_resources_helpers",
          kind = "directory",
          fileName = "helpers",
          sourceTreePrefix = "plugins/x/helpers",
          sourceTreeExclusions = DevPluginResourceExclusions(files = listOf("setup.py")),
        ),
      ),
    )

    val rendered: Map<String, String> = statements.render(syntheticIndex(dir, "intellij.x" to "//plugins/x:x.jar"))

    assertThat(rendered).containsExactly(entry(
      "intellij.x",
      """
      filegroup(
          name = "dev_dist_resources",
          srcs = glob(
              ["data/**"],
          ),
          visibility = ["//visibility:public"],
      )

      filegroup(
          name = "dev_dist_resources_helpers",
          srcs = glob(
              ["helpers/**"],
              exclude = ["helpers/**/setup.py"],
          ),
          visibility = ["//visibility:public"],
      )
      """.trimIndent() + "\n",
    ))
  }

  @Test
  fun `two sets of exclusions for one directory stop the run`() {
    val statements = DevDistResourceStatements()
    fun filtered(plugin: String, file: String) {
      statements.addPlanInputs(plugin = plugin, inputs = listOf(DevDistPluginRawInput(
        id = "module-resource:0:source",
        label = "//plugins/x:dev_dist_resources_helpers",
        kind = "directory",
        fileName = "helpers",
        sourceTreePrefix = "plugins/x/helpers",
        sourceTreeExclusions = DevPluginResourceExclusions(files = listOf(file)),
      )))
    }
    filtered(plugin = "intellij.x", file = "setup.py")
    filtered(plugin = "intellij.y", file = "setup.py")

    val error = assertThrows<IllegalStateException> { filtered(plugin = "intellij.z", file = "conftest.py") }

    assertThat(error).hasMessageContaining("The directory 'helpers' of '//plugins/x' has two sets of exclusions")
  }

  @Test
  fun `a filtered directory must name the filtered filegroup`() {
    val statements = DevDistResourceStatements()

    val error = assertThrows<IllegalStateException> {
      statements.addPlanInputs(plugin = "intellij.x", inputs = listOf(DevDistPluginRawInput(
        id = "module-resource:0:source",
        label = "//plugins/x:dev_dist_resources",
        kind = "directory",
        fileName = "helpers",
        sourceTreePrefix = "plugins/x/helpers",
        sourceTreeExclusions = DevPluginResourceExclusions(files = listOf("setup.py")),
      )))
    }

    assertThat(error).hasMessageContaining("instead of the filegroup 'dev_dist_resources_helpers' of its package")
  }

  @Test
  fun `filtered directories with one filegroup name stop the run`() {
    val statements = DevDistResourceStatements()
    statements.addPlanInputs(
      plugin = "intellij.x",
      inputs = listOf("a/b", "a_b").mapIndexed { index, directory ->
        DevDistPluginRawInput(
          id = "module-resource:$index:source",
          label = "//plugins/x:dev_dist_resources_a_b",
          kind = "directory",
          fileName = directory.substringAfterLast('/'),
          sourceTreePrefix = "plugins/x/$directory",
          sourceTreeExclusions = DevPluginResourceExclusions(files = listOf("setup.py")),
        )
      },
    )

    val error = assertThrows<IllegalStateException> { statements.render(syntheticIndex(dir, "intellij.x" to "//plugins/x:x.jar")) }

    assertThat(error).hasMessageContaining("The filtered directories of '//plugins/x' share a filegroup name")
  }

  @Test
  fun `a plan without a withResource input states nothing`() {
    val statements = DevDistResourceStatements()
    statements.addPlanInputs(
      plugin = "intellij.x",
      inputs = listOf(DevDistPluginRawInput(id = "layout-source:custom-asset:0:0", label = "@dev_launch_air_openui_renderer//:files", kind = "archive", fileName = "files")),
    )

    val rendered: Map<String, String> = statements.render(syntheticIndex(dir, "intellij.x" to "//plugins/x:x.jar"))

    assertThat(rendered).isEmpty()
  }

  @Test
  fun `the prefix of a source tree is read below its package`() {
    assertThat(resourceDirectoryBelowPackage("@community//plugins/terminal", "plugins/terminal/resources/shell-integrations"))
      .isEqualTo("resources/shell-integrations")
    assertThat(resourceDirectoryBelowPackage("//", "localization/properties/ja")).isEqualTo("localization/properties/ja")
    assertThat(resourceDirectoryBelowPackage("//ruby/backend", "ruby/coverage/resources/rb")).isNull()
  }
}
