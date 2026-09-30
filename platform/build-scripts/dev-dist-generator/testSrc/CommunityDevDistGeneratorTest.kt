// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CommunityDevDistGeneratorTest {
  @TempDir
  lateinit var dir: Path

  @Test
  fun `a run from the community root and a run from the monorepo root find one community root`() {
    val communityRoot = Files.createDirectories(dir.resolve("community"))
    Files.writeString(communityRoot.resolve(".community.root.marker"), "")
    val nested = Files.createDirectories(communityRoot.resolve("platform/build-scripts"))

    assertThat(findCommunityRoot(communityRoot)).isEqualTo(communityRoot)
    assertThat(findCommunityRoot(dir)).isEqualTo(communityRoot)
    assertThat(findCommunityRoot(nested)).isEqualTo(communityRoot)
  }

  @Test
  fun `a directory without a community root above it fails and names the start`() {
    val start = Files.createDirectories(dir.resolve("elsewhere"))

    assertThatThrownBy { findCommunityRoot(start) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(start.toString())
      .hasMessageContaining(".community.root.marker")
  }

  /**
   * The community binary renders the community half with the community root as the project home and the targets JSON
   * as the converter wrote it. The monorepo run renders it with the monorepo root and the jar paths remapped, see
   * [communityTargetsSeenFromMonorepo]. A generated file holds no jar path, so both renders state one text per file.
   * The test compares the two renders against the checkout, so an equal diff list means equal files.
   *
   * The test needs the community targets JSON: the property of `//build:dev_dist_generator_identity_test`, or the
   * `community/build/bazel-targets.json` of the checkout. It is skipped without one.
   */
  @Test
  fun `the community binary and the monorepo run render one text for the community half`() {
    val communityRoot = checkoutCommunityRoot()
    val targetsFile = communityTargetsJson(communityRoot)
    assumeTrue(Files.exists(targetsFile), "No community targets JSON at $targetsFile. Run ./build/jpsModelToBazelCommunityOnly.cmd in the community root.")
    val targets = readBazelTargetsJson(targetsFile)

    val binaryRun = computeCommunityHalf(communityRoot = communityRoot, projectHome = communityRoot, targets = targets, verifyPlanUnits = false)
    val monorepoRun = computeCommunityHalf(
      communityRoot = communityRoot,
      projectHome = communityRoot.parent,
      targets = communityTargetsSeenFromMonorepo(targets),
      verifyPlanUnits = false,
    )

    assertThat(renderedDiffs(monorepoRun, communityRoot)).isEqualTo(renderedDiffs(binaryRun, communityRoot))
  }
}

/**
 * The community root of this checkout: the first of the home properties, the Bazel workspace, the location of this class
 * and the working directory that has a community root at or above it, see [findCommunityRoot].
 */
private fun checkoutCommunityRoot(): Path {
  val starts = listOfNotNull(
    System.getProperty("intellij.build.community.home.path"),
    System.getProperty("intellij.build.ultimate.home.path"),
    System.getProperty("idea.home.path"),
    System.getenv("BUILD_WORKSPACE_DIRECTORY"),
    CommunityDevDistGeneratorTest::class.java.protectionDomain?.codeSource?.location?.toURI()?.let { Path.of(it).toString() },
    System.getProperty("user.dir"),
  )
  for (start in starts) {
    try {
      return findCommunityRoot(Path.of(start))
    }
    catch (ignored: IllegalStateException) {
      continue
    }
  }
  error("No community root at or above $starts")
}

/** The files of [run] that differ from the checkout under [root], as the path, the change and the rendered text, sorted by path. */
private fun renderedDiffs(run: DevDistBazelComputes, root: Path): List<Triple<String, String, String>> {
  return listOf(run.sections.finish(commitChanges = false), run.plan.finish(commitChanges = false))
    .flatMap { it.diffs }
    .map { diff: FileDiff -> Triple(root.relativize(diff.path).toString(), diff.changeType.name, diff.expectedContent) }
    .sortedBy { it.first }
}
