// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * What asking a module scope a question costs, in the classic ([ClassicRootContainer]) and the entry-set
 * ([EntrySetRootContainer]) mode.
 *
 * [ModuleScopeMemoryTest] gates the *memory* of the entry-set container, which is the reason it exists: a scope
 * stores the ~95 entities it reaches instead of the ~5100 roots behind them. The CPU side was left as an argument
 * rather than a measurement - "`contains` gains one indirection, and the `ProjectFileIndex` query that precedes it
 * is expected to dominate" - and this is that measurement. Results are in
 * `docs/IntelliJ-Platform/4_man/Lang/Shared-Sources/Module-Scope-Memory.md`.
 *
 * ## What is measured, and why each one
 *
 * | subtest | path | what could go wrong |
 * |---|---|---|
 * | `contains` over source files | file index + one entity probe | the extra probe is not free |
 * | `contains` over library files | the same without the test-root branch | isolates [EntityRootOrder] from the probe |
 * | `contains` with test roots | [EntrySetRootContainer.acceptsRoot] consults [EntityRootOrder.indexOf] | the one branch that touches the shared table on a hit |
 * | `compare` over file pairs | [RootContainerScope.compareByRank] | it needs each file's owner, so it calls the *plural* `getModuleSourceOrLibraryClassesRoots`, which resolves one, where the old container called the singular one |
 * | scope construction | `getEnumeratedEntries()` instead of `getRootEntries()` | expected to be the big win: building a scope no longer resolves a root |
 *
 * ## What it asserts, and what it only reports
 *
 * It asserts two facts, and no timing. Both modes must answer every question the same way, and each mode must
 * have built the container it asked for. Both arms force `intellij.platform.shared.source.support` for the second
 * one: multiverse is the registry default in this repository, so a run that forgets it measures the same
 * container twice, which is what [ModuleScopeMemoryTest] did until its container assertion was added.
 *
 * The times are **published as metrics and printed, not asserted**. A ratio bound was tried first and removed,
 * because it cannot be made honest here. A multiverse `contains` allocates a list and a descriptor per call, so a
 * loop of 800 000 calls measures the garbage collector as much as the lookup. Over three identical runs the
 * multiverse `contains(library)` came out at 526, 249 and 127 ns per call while the classic arm held 35 to 38, and
 * the container's own share, which is `contains` minus the file-index query in front of it, came out *negative* in
 * half the runs. Any bound loose enough to survive that noise is too loose to catch a defect, and a flaky gate is
 * worse than none. The regression signal belongs where the other IntelliJ benchmarks keep it: the metric history
 * on the build server.
 *
 * So read this test as an instrument. [ModuleScopeMemoryTest] is the gate.
 */
@StressTestApplication
@TestApplication
@PerformanceUnitTest
internal class ModuleScopeLookupBenchmarkTest {
  @Test
  fun `jps monorepo`() {
    // The shape with least to share: ~1.6 roots per entity, so the entry-set container's per-call overhead is
    // amortised over almost nothing. If an indirection costs anything, it shows here first.
    measure(
      ProjectShape(
        id = "jps-monorepo",
        moduleCount = 300,
        sourceRootsAt = { 2 },
        testSourceRootsAt = { 1 },
        layerSize = 40,
        exportedDepsPerModule = 2,
        ownLibraryRoots = 1,
        sdkRootCount = 3,
      )
    )
  }

  @Test
  fun `bazel fine grained`() {
    // The opposite end: thousands of roots behind ~148 entities, which is where the entry-set container should be
    // at its best - a `contains` probe sees a map 35x smaller than the classic one.
    measure(
      ProjectShape(
        id = "bazel-fine-grained",
        moduleCount = 300,
        sourceRootsAt = { index ->
          when {
            index < 4 -> 400
            index % 7 == 0 -> 50
            index % 3 == 0 -> 4
            else -> 1
          }
        },
        testSourceRootsAt = { index -> if (index % 3 == 0) 2 else 0 },
        layerSize = 10,
        exportedDepsPerModule = 5,
        ownLibraryRoots = 1,
        sdkRootCount = 69,
      )
    )
  }

  private fun measure(shape: ProjectShape) {
    val classic = runShape(shape, multiverse = false)
    val multiverse = runShape(shape, multiverse = true)

    report(shape, classic, multiverse)

    for (operation in OPERATIONS) {
      if (!operation.comparesAnswers) continue
      val c = classic.getValue(operation.name)
      val m = multiverse.getValue(operation.name)
      // The one assertion that is a fact rather than a timing: both containers must answer the same question the
      // same way. It is also what proves the timings compare like with like, instead of one mode doing less work.
      assertEquals(c.answers, m.answers) {
        "${shape.id}/${operation.name}: the two modes must answer the same, got ${m.answers} vs ${c.answers}"
      }
    }
  }

  private fun runShape(shape: ProjectShape, multiverse: Boolean): Map<String, Timing> =
    withScopeProject(shape, multiverse) { project ->
      val probes = Probes.of(project, multiverse)
      OPERATIONS.associateBy({ it.name }, { time(shape, multiverse, it, probes) })
    }

  /**
   * Times one operation, and keeps the *fastest* attempt rather than their mean.
   *
   * Both halves matter. An attempt repeats the operation until it has made [Operation.minCalls] calls, because an
   * attempt of a few hundred microseconds measures the garbage collector; and the minimum is the robust statistic
   * for a microbenchmark, because noise can only ever make a run slower.
   */
  private fun time(shape: ProjectShape, multiverse: Boolean, operation: Operation, probes: Probes): Timing {
    var answers = 0L
    var bestNanosPerCall = Double.MAX_VALUE
    var totalCalls = 0L
    var totalNanos = 0L
    Benchmark.newBenchmark("${shape.id}/${operation.name}/${mode(multiverse)}") {
      var calls = 0L
      val start = System.nanoTime()
      runReadActionBlocking {
        do {
          val run = operation.body(probes)
          calls += run.calls
          answers = run.answers
        }
        while (calls < operation.minCalls)
      }
      val nanos = System.nanoTime() - start
      totalCalls += calls
      totalNanos += nanos
      if (calls > 0) {
        bestNanosPerCall = minOf(bestNanosPerCall, nanos.toDouble() / calls)
      }
    }.attempts(ATTEMPTS).warmupIterations(1).startAsSubtest()
    return Timing(answers = answers, calls = totalCalls, nanos = totalNanos, nanosPerCall = bestNanosPerCall)
  }

  private fun report(shape: ProjectShape, classic: Map<String, Timing>, multiverse: Map<String, Timing>) {
    val out = StringBuilder()
    out.append("\n=== ").append(shape.id).append(" (").append(shape.moduleCount).append(" modules) ===\n")
    out.append("%-26s %-11s %12s %14s %12s %8s\n".format("operation", "mode", "calls", "total ms", "best ns/call", "ratio"))
    for (operation in OPERATIONS) {
      val c = classic.getValue(operation.name)
      val m = multiverse.getValue(operation.name)
      for ((mode, t) in listOf("classic" to c, "multiverse" to m)) {
        out.append("%-26s %-11s %12d %14.1f %12.0f %8s\n".format(
          operation.name, mode, t.calls, t.nanos / 1_000_000.0, t.nanosPerCall,
          if (mode == "classic") "-" else "%.2f".format(m.nanosPerCall / c.nanosPerCall)))
      }
    }
    println(out)
  }

  private fun mode(multiverse: Boolean) = if (multiverse) "multiverse" else "classic"
}

/**
 * The files and scopes a run asks about, collected once per project so that the timed section does nothing but
 * call the scope.
 *
 * The scopes are the runtime mask (`MODULES or LIBRARIES`), which is the 9.5 GB of cached scopes in the
 * IJPL-255937 snapshot and the only mask that reaches libraries and the SDK at all.
 */
private class Probes(
  val scopes: List<ModuleWithDependenciesScope>,
  val sourceFiles: List<VirtualFile>,
  val libraryFiles: List<VirtualFile>,
  val testFiles: List<VirtualFile>,
  val modules: List<Module>,
  val project: Project,
  val multiverse: Boolean,
) {
  /** Scopes the other operations iterate, so that the file-index subtest makes exactly as many calls. */
  val scopeCount: Int get() = minOf(PROBE_SCOPES, scopes.size)

  companion object {
    fun of(project: Project, multiverse: Boolean): Probes {
      val modules = ModuleManager.getInstance(project).modules.toList()
      val scopes = modules.map { module ->
        val scope = module.getModuleRuntimeScope(false) as ModuleWithDependenciesScope
        assertEquals(expectedContainerClass(multiverse), scope.rootContainer.javaClass) {
          "the runtime scope of ${module.name} is backed by the wrong container"
        }
        scope
      }

      val sourceFiles = ArrayList<VirtualFile>()
      val testFiles = ArrayList<VirtualFile>()
      val libraryFiles = ArrayList<VirtualFile>()
      runReadActionBlocking {
        for (module in modules.take(PROBE_MODULES)) {
          val rootManager = ModuleRootManager.getInstance(module)
          for (contentEntry in rootManager.contentEntries) {
            for (sourceFolder in contentEntry.sourceFolders) {
              val root = sourceFolder.file ?: continue
              (if (sourceFolder.isTestSource) testFiles else sourceFiles).add(root)
            }
          }
          OrderEnumerator.orderEntries(module).withoutSdk().withoutModuleSourceEntries()
            .classes().roots.toCollection(libraryFiles)
        }
      }

      return Probes(scopes = scopes,
                    sourceFiles = sourceFiles.distinct().take(PROBE_FILES),
                    libraryFiles = libraryFiles.distinct().take(PROBE_FILES),
                    testFiles = testFiles.distinct().take(PROBE_FILES),
                    modules = modules,
                    project = project,
                    multiverse = multiverse)
    }
  }
}

/**
 * The mask of the 9.5 GB of `myOptions == 6` scopes in the IJPL-255937 snapshot.
 *
 * It must stay declared above its use: a `const val` that `or`s two constants resolves to `BigInteger.or` when it
 * is referenced earlier in the file, and the type error then points somewhere else entirely.
 */
private const val RUNTIME_MASK: Int = MODULES or LIBRARIES

/** What one timed attempt did: how many calls it made, and how many of them said yes. */
private class Run(val calls: Long, val answers: Long)

/**
 * One operation's result. [nanos] and [calls] are the totals over every attempt, for the printed table;
 * [nanosPerCall] is the *fastest* attempt, which is what the assertions use.
 */
private class Timing(val answers: Long, val calls: Long, val nanos: Long, val nanosPerCall: Double)

private const val FILE_INDEX = "fileIndex(source)"
private const val FILE_INDEX_LIBRARY = "fileIndex(library)"
private const val CONTAINS_SOURCE = "contains(source)"
private const val CONTAINS_LIBRARY = "contains(library)"
private const val CONTAINS_TEST = "contains(testRoot)"
private const val COMPARE = "compare(pairs)"
private const val BUILD_SCOPE = "buildScope"

/**
 * One measured operation.
 *
 * @param minCalls calls an attempt must make before it stops. A pass over the probe files is only a few thousand
 *                 calls on the smaller shape, which times the garbage collector rather than the operation, so an
 *                 attempt repeats the pass until it reaches this number.
 * @param comparesAnswers whether the two modes must return the same answer count. False only for [FILE_INDEX],
 *                        which calls a different file-index method per mode on purpose.
 */
private class Operation(
  val name: String,
  val minCalls: Long,
  val comparesAnswers: Boolean = true,
  val body: (Probes) -> Run,
)

private val OPERATIONS: List<Operation> = listOf(
  // Not a scope call at all: the ProjectFileIndex query each mode's `contains` makes before it reaches the
  // container - the singular one in classic mode, the plural one in multiverse mode. Subtracting this from
  // `contains(source)` is what separates "the container got slower" from "the query in front of it did", which
  // the design notes assumed would dominate.
  Operation(FILE_INDEX, MIN_CALLS, comparesAnswers = false) { probes -> fileIndexOver(probes, probes.sourceFiles) },
  // The same for the files `compare(pairs)` asks about. A library root resolves its owner through the workspace
  // model snapshot and the library bridge, where a module source root reads it off the file set, and on the JPS
  // shape that made the library query half as expensive again as the source one.
  Operation(FILE_INDEX_LIBRARY, MIN_CALLS, comparesAnswers = false) { probes -> fileIndexOver(probes, probes.libraryFiles) },
  Operation(CONTAINS_SOURCE, MIN_CALLS) { probes -> containsOver(probes, probes.sourceFiles) },
  Operation(CONTAINS_LIBRARY, MIN_CALLS) { probes -> containsOver(probes, probes.libraryFiles) },
  Operation(CONTAINS_TEST, MIN_CALLS) { probes -> containsOver(probes, probes.testFiles) },
  Operation(COMPARE, MIN_CALLS) { probes ->
    var calls = 0L
    var answers = 0L
    for (scope in probes.scopes.take(PROBE_SCOPES)) {
      val files = probes.libraryFiles
      for (index in 1 until files.size) {
        calls++
        if (scope.compare(files[index - 1], files[index]) > 0) answers++
      }
    }
    Run(calls, answers)
  },
  // One call per module and no repeat, unlike every other operation: the second pass would hit the cache this one
  // empties, so it would measure a cache lookup instead of the OrderEnumerator walk.
  Operation(BUILD_SCOPE, minCalls = 0) { probes ->
    val cache = probes.project.service<ModuleWithDependenciesScopeCache>()
    cache.clear()
    var calls = 0L
    var answers = 0L
    for (module in probes.modules.take(PROBE_SCOPES)) {
      calls++
      // Counting the modules the built scope searches keeps the two modes honestly comparable without
      // materialising roots, which would measure the thing the container deliberately stopped doing.
      if (cache.getCachedScope(module, RUNTIME_MASK).isSearchInLibraries) answers++
    }
    Run(calls, answers)
  },
)

private fun fileIndexOver(probes: Probes, files: List<VirtualFile>): Run {
  var calls = 0L
  var answers = 0L
  val index = ProjectFileIndex.getInstance(probes.project)
  repeat(probes.scopeCount) {
    for (file in files) {
      calls++
      if (probes.multiverse) {
        if (index.getModuleSourceOrLibraryClassesRoots(file).isNotEmpty()) answers++
      }
      else {
        if (index.getModuleSourceOrLibraryClassesRoot(file) != null) answers++
      }
    }
  }
  return Run(calls, answers)
}

private fun containsOver(probes: Probes, files: List<VirtualFile>): Run {
  var calls = 0L
  var answers = 0L
  for (scope in probes.scopes.take(PROBE_SCOPES)) {
    for (file in files) {
      calls++
      if (scope.contains(file)) answers++
    }
  }
  return Run(calls, answers)
}

/** Modules whose roots become probe files; enough to cross several entities, few enough to keep setup cheap. */
private const val PROBE_MODULES = 20

private const val PROBE_FILES = 200

/** Scopes each operation runs over. The cost is `PROBE_SCOPES * PROBE_FILES` calls per attempt. */
private const val PROBE_SCOPES = 50

private const val ATTEMPTS = 3

/**
 * Calls an attempt makes before it stops. One pass over the probe files is 2000 calls on the smaller shape, about
 * 0.4 ms, where a single collection moves the result by more than the bounds below allow. At this number an
 * attempt runs for tens of milliseconds.
 */
private const val MIN_CALLS = 200_000L


