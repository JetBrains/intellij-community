// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.buildSpan
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import org.jetbrains.intellij.build.telemetry.withoutTracer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * The community binary: it regenerates the dev-distribution files of the community half.
 *
 * Run `bazel run //build:dev_dist_generator` in `community/`, or `bazel run @community//build:dev_dist_generator` in the
 * monorepo root. Both runs find one community root, see [findCommunityRoot], so both write the same bytes.
 *
 * Arguments:
 * - `--check`: write nothing, print each file that is out of sync, and exit with 1 when a file is out of sync.
 * - `--verify-plan-units`: also compute the plan of each request and check it against its plan unit.
 *
 * The binary writes only below the community root. It writes no Product DSL XML, because the ultimate tool
 * `bazel run //platform/buildScripts:plugin-model-tool` is the one writer of that XML. The ultimate tool also runs this
 * community half, through the same [computeCommunityHalf].
 */
@ApiStatus.Internal
object CommunityDevDistGenerator {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = parseCommunityDevDistOptions(args)
    val communityRoot = findCommunityRoot(Path.of(System.getenv(BUILD_WORKSPACE_DIRECTORY_ENV) ?: System.getProperty("user.dir")))
    var outOfSync = false
    withoutTracer {
      val result = generateCommunityDevDist(communityRoot = communityRoot, commit = options.commit, verifyPlanUnits = options.verifyPlanUnits)
      for (diff in result.diffs) {
        println("out of sync: ${communityRoot.relativize(diff.path)} (${diff.changeType})")
      }
      val changed = if (options.commit) "${result.files.count { it.status != FileChangeStatus.UNCHANGED }} changed" else "${result.diffs.size} out of sync"
      println("Community half $communityRoot: ${result.files.size} dev-distribution files, $changed")
      outOfSync = !options.commit && result.diffs.isNotEmpty()
    }
    if (outOfSync) {
      exitProcess(1)
    }
  }
}

/** The environment variable in which `bazel run` names the workspace that the command started in. */
private const val BUILD_WORKSPACE_DIRECTORY_ENV: String = "BUILD_WORKSPACE_DIRECTORY"

/** The file at the root of the community checkout. */
internal const val COMMUNITY_ROOT_MARKER: String = ".community.root.marker"

/** The arguments of the community binary. */
private class CommunityDevDistOptions(@JvmField val commit: Boolean, @JvmField val verifyPlanUnits: Boolean)

/** Parses the arguments of [CommunityDevDistGenerator]. An unknown argument stops the run and names the argument. */
private fun parseCommunityDevDistOptions(args: Array<String>): CommunityDevDistOptions {
  val unknown = args.filterNot { it == "--check" || it == "--verify-plan-units" }
  require(unknown.isEmpty()) { "Unknown arguments $unknown. The community binary accepts --check and --verify-plan-units." }
  return CommunityDevDistOptions(commit = "--check" !in args, verifyPlanUnits = "--verify-plan-units" in args)
}

/**
 * The community checkout that [start] is in, or the one below it: the first directory from [start] up that holds
 * [COMMUNITY_ROOT_MARKER], or whose `community/` holds it.
 *
 * `bazel run` from `community/` starts at the community root. `bazel run` from the monorepo root starts one level above
 * it. The JPS-to-Bazel converter finds its community root in the same way.
 */
@ApiStatus.Internal
fun findCommunityRoot(start: Path): Path {
  var current: Path? = start.toAbsolutePath().normalize()
  while (current != null) {
    if (Files.exists(current.resolve(COMMUNITY_ROOT_MARKER))) {
      return current
    }
    val nested = current.resolve(COMMUNITY_ROOT_DIRECTORY)
    if (Files.exists(nested.resolve(COMMUNITY_ROOT_MARKER))) {
      return nested
    }
    current = current.parent
  }
  error("No community root at $start or above it: no directory holds $COMMUNITY_ROOT_MARKER")
}

/**
 * Renders the community half over the community checkout [communityRoot], and writes it when [commit]. Otherwise, the
 * result reports each changed file as a diff.
 *
 * The run reads the community targets JSON, see [communityTargetsJson]. Its jar paths are relative to [communityRoot].
 * The run fails when the file does not exist, and the message names the converter that writes it.
 */
internal fun generateCommunityDevDist(communityRoot: Path, commit: Boolean, verifyPlanUnits: Boolean): DevDistHalvesFiles {
  val targetsFile = communityTargetsJson(communityRoot)
  check(Files.exists(targetsFile)) {
    "The community half needs $targetsFile. Run ./build/jpsModelToBazelCommunityOnly.cmd in the community root first."
  }
  val targets = buildSpan("load community bazel-targets.json") { readBazelTargetsJson(targetsFile) }
  val half = computeCommunityHalf(communityRoot = communityRoot, projectHome = communityRoot, targets = targets, verifyPlanUnits = verifyPlanUnits)
  val results = listOf(half.sections.finish(commitChanges = commit), half.plan.finish(commitChanges = commit))
  return DevDistHalvesFiles(files = results.flatMap { it.files }, diffs = results.flatMap { it.diffs })
}
