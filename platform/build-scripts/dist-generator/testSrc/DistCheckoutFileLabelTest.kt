// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.distGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The label of a checkout file that a plan names, such as an icon of a macOS app: the package of the file, the
 * repository prefix, and the export that makes the label resolve.
 */
class DistCheckoutFileLabelTest {
  @TempDir
  lateinit var dir: Path

  private val targets =
    BazelTargetsInfo.TargetsFile(modules = emptyMap(), projectLibraries = emptyMap(), pluginDistributionTargets = emptyMap())

  private fun write(relative: String, text: String = ""): Path {
    val file = dir.resolve(relative)
    Files.createDirectories(file.parent)
    Files.writeString(file, text)
    return file
  }

  private fun exports(vararg files: String): String {
    return "exports_files(\n    [\n" + files.joinToString(separator = "") { "        \"$it\",\n" } + "    ],\n)\n"
  }

  @Test
  fun `a file takes the label of its nearest package in its repository`() {
    val index = DistBazelIndex(targets = targets, projectRoot = dir)
    write("BUILD.bazel", exports("product/images/mac/product.icns"))
    val root = write("product/images/mac/product.icns")
    write("rider/build/BUILD.bazel", exports("images/mac/product_EAP.icns"))
    val nested = write("rider/build/images/mac/product_EAP.icns")
    write("community/build/BUILD.bazel", exports("images/mac/product.icns"))
    val community = write("community/build/images/mac/product.icns")

    assertThat(checkoutFileLabel(root, index)).isEqualTo("//:product/images/mac/product.icns")
    assertThat(checkoutFileLabel(nested, index)).isEqualTo("//rider/build:images/mac/product_EAP.icns")
    assertThat(checkoutFileLabel(community, index)).isEqualTo("@community//build:images/mac/product.icns")

    // The community half spells a community label for its own packages.
    val communityRoot = dir.resolve("community")
    val communityIndex =
      DistBazelIndex(targets = targets, projectRoot = communityRoot, communityRoot = communityRoot, planPackageIsCommunity = true)
    assertThat(checkoutFileLabel(community, communityIndex)).isEqualTo("//build:images/mac/product.icns")
  }

  @Test
  fun `a file that no exports_files list names fails and names the build file`() {
    val index = DistBazelIndex(targets = targets, projectRoot = dir)
    val buildFile = write("p/BUILD.bazel", "filegroup(\n    name = \"images\",\n    srcs = [\"a.icns\"],\n)\n" + exports("b.icns"))
    val icon = write("p/a.icns")

    assertThatThrownBy { checkoutFileLabel(icon, index) }
      .hasMessageContaining("The label //p:a.icns does not resolve: no exports_files list of $buildFile names \"a.icns\"")
    assertThatThrownBy { checkoutFileLabel(dir.resolve("p/missing.icns"), index) }
      .hasMessageContaining("does not exist")
  }
}
