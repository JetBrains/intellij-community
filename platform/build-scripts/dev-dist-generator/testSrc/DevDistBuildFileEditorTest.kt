// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DevDistBuildFileEditorTest {
  private val bridge = "@jps_dynamic_deps_ultimate//:targets.bzl"
  private val macroFile = "@community//platform/build-scripts/bazel-rules:dev_dist_plugin.bzl"

  @Test
  fun `a symbol the sections take from one file leaves every other file`() {
    val manager = BazelLoadStatementManager()
    manager.insert(mapOf(bridge to setOf("dev_dist_plugin")))
    manager.insert("""load("$macroFile", "dev_dist_plugin")""")
    manager.insert("""load("$bridge", "MODULE_TARGETS")""")

    manager.keepOneOrigin(mapOf(bridge to setOf("dev_dist_plugin")))

    assertThat(manager.getResult()).isEqualTo("""load("$bridge", "MODULE_TARGETS", "dev_dist_plugin")""")
  }

  @Test
  fun `the result binds a moved symbol once`() {
    val editor = DevDistBuildFileEditor(
      """
      load("$macroFile", "dev_dist_plugin")
      load("$bridge", "MODULE_TARGETS")

      ### auto-generated section `dev intellij.sample` start
      dev_dist_plugin(
          main_module = "intellij.sample",
          module_targets = MODULE_TARGETS,
      )
      ### auto-generated section `dev intellij.sample` end
      """.trimIndent(),
    )
    editor.setSection("dev intellij.sample", "dev_dist_plugin(\n    main_module = \"intellij.sample\",\n)")

    val result = editor.result(mapOf(bridge to setOf("dev_dist_plugin")))

    assertThat(result).isEqualTo(
      """
      load("$bridge", "dev_dist_plugin")

      ### auto-generated section `dev intellij.sample` start
      dev_dist_plugin(
          main_module = "intellij.sample",
      )
      ### auto-generated section `dev intellij.sample` end
      """.trimIndent() + "\n",
    )
  }

  @Test
  fun `a new dev section goes before the first converter section`() {
    val editor = DevDistBuildFileEditor(
      """
      load("@rules_jvm//:jvm.bzl", "jvm_library")

      ### auto-generated section `build intellij.sample` start
      jvm_library(name = "sample")
      ### auto-generated section `build intellij.sample` end
      """.trimIndent() + "\n",
    )
    editor.setSection("dev intellij.sample", "filegroup(\n    name = \"dev_dist_resources\",\n    srcs = glob([\"resources/**\"]),\n)")

    val result = editor.result(emptyMap())

    assertThat(result).isEqualTo(
      """
      load("@rules_jvm//:jvm.bzl", "jvm_library")

      ### auto-generated section `dev intellij.sample` start
      filegroup(
          name = "dev_dist_resources",
          srcs = glob(["resources/**"]),
      )
      ### auto-generated section `dev intellij.sample` end

      ### auto-generated section `build intellij.sample` start
      jvm_library(name = "sample")
      ### auto-generated section `build intellij.sample` end
      """.trimIndent() + "\n",
    )
  }
}
