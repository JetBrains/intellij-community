// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectModelTreeMain")

package org.jetbrains.intellij.build.devServer

import org.jetbrains.intellij.build.dev.materializeProjectModelTree
import java.nio.file.Path

/**
 * Lays the declared JPS project-model files out as a checkout-shaped tree, once, for others to read.
 *
 * Every dev-distribution fragment needs that tree, and building its own costs 7 432 file copies - as much as the
 * assembly that follows, which is what the comment on [materializeProjectModelTree] says. One fragment could afford it;
 * a distribution split into a dozen of them cannot, so the tree becomes an artifact of its own and the fragments read
 * it through `--project-dir`.
 *
 * Deliberately not a mode of `DevDistMain`: that entry point assembles a distribution, and this one produces an input
 * for it. Keeping them apart is also what lets the tree be built once and shared by fragments of different products.
 */
fun main(args: Array<String>) {
  var manifest: Path? = null
  var outputDir: Path? = null
  var traceFile: Path? = null
  for (arg in args) {
    val separator = arg.indexOf('=')
    require(arg.startsWith("--") && separator > 2) { "Expected an option in the '--key=value' form, but got '$arg'" }
    val value = Path.of(arg.substring(separator + 1)).toAbsolutePath().normalize()
    when (val name = arg.substring(0, separator)) {
      "--project-manifest" -> manifest = value
      "--output-dir" -> outputDir = value
      TRACE_FILE_OPTION -> traceFile = value
      else -> error("Unknown option '$name'")
    }
  }

  runDevDistJob(traceFile = traceFile, jobName = "materialize project model tree") {
    val tree = materializeProjectModelTree(
      manifest = requireNotNull(manifest) { "--project-manifest is required" },
      target = requireNotNull(outputDir) { "--output-dir is required" },
    )
    println("Project model tree materialized into $tree")
  }
}
