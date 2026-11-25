// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.distGenerator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DistBuildFileEditorTest {
  private val otherFile = "//build:intellij_dev_ultimate.bzl"
  private val macroFile = "@community//platform/build-scripts/bazel-rules:dist_plugin.bzl"

  @Test
  fun `a symbol the sections take from one file leaves every other file`() {
    val manager = BazelLoadStatementManager()
    manager.insert(mapOf(macroFile to setOf("dist_plugin")))
    manager.insert("""load("$otherFile", "intellij_dev_run_configuration", "dist_plugin")""")

    manager.keepOneOrigin(mapOf(macroFile to setOf("dist_plugin")))

    assertThat(manager.getResult()).isEqualTo(
      """load("$macroFile", "dist_plugin")""" + "\n" + """load("$otherFile", "intellij_dev_run_configuration")""",
    )
  }

  @Test
  fun `the result binds a moved symbol once`() {
    val editor = DistBuildFileEditor(
      """
      load("$otherFile", "dist_plugin")

      ### auto-generated section `dev intellij.sample` start
      dist_plugin(
          main_module = "intellij.sample",
      )
      ### auto-generated section `dev intellij.sample` end
      """.trimIndent(),
    )
    editor.setSection(
      "dev intellij.sample",
      "dist_plugin(\n    main_module = \"intellij.sample\",\n    module_labels = {\"intellij.sample\": \"//sample\"},\n)",
    )

    val result = editor.result(mapOf(macroFile to setOf("dist_plugin")))

    assertThat(result).isEqualTo(
      """
      load("$macroFile", "dist_plugin")

      ### auto-generated section `dev intellij.sample` start
      dist_plugin(
          main_module = "intellij.sample",
          module_labels = {"intellij.sample": "//sample"},
      )
      ### auto-generated section `dev intellij.sample` end
      """.trimIndent() + "\n",
    )
  }

  @Test
  fun `a new dev section goes before the first converter section`() {
    val editor = DistBuildFileEditor(
      """
      load("@rules_jvm//:jvm.bzl", "jvm_library")

      ### auto-generated section `build intellij.sample` start
      jvm_library(name = "sample")
      ### auto-generated section `build intellij.sample` end
      """.trimIndent() + "\n",
    )
    editor.setSection("dev intellij.sample", "filegroup(\n    name = \"dist_resources\",\n    srcs = glob([\"resources/**\"]),\n)")

    val result = editor.result(emptyMap())

    assertThat(result).isEqualTo(
      """
      load("@rules_jvm//:jvm.bzl", "jvm_library")

      ### auto-generated section `dev intellij.sample` start
      filegroup(
          name = "dist_resources",
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
