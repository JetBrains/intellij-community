// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * A section another writer owns survives the removal of the sections around it with one blank line on each side.
 *
 * `plugin-model-tool` writes the `dev <module>` section between the `iml` and the `test` section this run rewrites.
 * Without the collapse, the blank lines of both removed neighbours meet around it, and the Starlark formatter rejects the
 * result.
 */
internal class BazelFileUpdaterRemoveSectionsTest {

  @JvmField
  @Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `a kept section between two removed sections keeps one blank line on each side`() {
    val file = buildFile("""
      load("@rules_jvm//:jvm.bzl", "jvm_library")

      ### auto-generated section `build sample` start
      jvm_library(name = "sample")
      ### auto-generated section `build sample` end

      ### auto-generated section `iml sample` start
      exports_files(["sample.iml"])
      ### auto-generated section `iml sample` end

      ### auto-generated section `dev sample` start
      dev_dist_plugin(descriptor_module = ":sample")
      ### auto-generated section `dev sample` end

      ### auto-generated section `test sample` start
      jps_test(name = "sample_test")
      ### auto-generated section `test sample` end
    """.trimIndent())

    val updater = BazelFileUpdater(file)
    updater.removeSections("build")
    updater.removeSections("iml ")
    updater.removeSections("test")
    updater.save()

    assertEquals("""
      load("@rules_jvm//:jvm.bzl", "jvm_library")

      ### auto-generated section `dev sample` start
      dev_dist_plugin(descriptor_module = ":sample")
      ### auto-generated section `dev sample` end
    """.trimIndent() + "\n", file.readText())
  }

  private fun buildFile(content: String): Path {
    val file = tempFolder.newFile("BUILD.bazel").toPath()
    file.writeText(content + "\n")
    return file
  }
}
