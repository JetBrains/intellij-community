// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ref.GCUtil
import it.unimi.dsi.fastutil.HashCommon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.sun.management.HotSpotDiagnosticMXBean
import java.lang.management.ManagementFactory

/**
 * Measures how much memory [ModuleWithDependenciesScopeCache] costs per root, for several shapes of project model,
 * in the classic ([ClassicRootContainer]) and the multiverse ([EntrySetRootContainer]) mode.
 *
 * The full investigation, including the heap-snapshot numbers quoted below and how they were obtained, is in
 * `docs/IntelliJ-Platform/4_man/Lang/Shared-Sources/Module-Scope-Memory.md`.
 *
 * ## Why shapes
 *
 * The per-container cost is meaningless without a shape: the same code costs ~88 roots per module scope in the
 * monorepo opened as JPS (IJPL-252901) and 1370 to 33390 in the same monorepo opened with Bazel (IJPL-255937),
 * because Bazel's fine-grained targets put tens of source roots behind a single [ModuleSourceOrderEntry] while JPS
 * puts one or two. Measured on the Bazel-opened monorepo (36826 modules, 140721 source roots, up to 10677 in one):
 *
 * | module                            | distinct roots | distinct order entries | roots/entry |
 * |-----------------------------------|----------------|------------------------|-------------|
 * | `intellij.platform.core`          |           1370 |                    140 |         9.8 |
 * | `intellij.platform.analysis.impl` |           4096 |                    432 |         9.5 |
 * | `intellij.java.impl`              |          19847 |                   1072 |        18.5 |
 * | `intellij.idea.community.main`    |          33390 |                   4278 |         7.8 |
 *
 * `roots/entry` is exactly the factor the descriptor interning of IJPL-255937 saves, so a shape that does not
 * reproduce it cannot tell whether that fix works. The shapes below are calibrated against those numbers.
 *
 * ## What the snapshot says the answer is
 *
 * In the snapshot attached to IJPL-255937, 109331 cached containers held 562191426 roots in 1089361913 map slots,
 * costing 16.56 GiB at 31.63 B/root. Of that, 98% is the raw `(VirtualFile, long)` slot and only 1.9% is descriptors
 * and their arrays - without interning the same snapshot would cost about 33 GiB. So the interning works, and what
 * remains to optimise is the per-root slot, not the descriptors.
 *
 * Packing that value into an `int` (IJPL-255937) halved it, bringing the same snapshot to about 12 GiB. What
 * [EntrySetRootContainer] does instead is stop storing the slot: a scope keeps one entry per module, library or SDK
 * it reaches - ~95 against 5100 roots in that snapshot - and the roots behind each of them live once per project in
 * [ScopeEntityRoots]. So the per-root cost of a *cached scope* is no longer a slot but a fraction of one.
 *
 * ## What is asserted
 *
 * Only scale-invariant ratios, so [SCALE] and the module counts can change without touching expectations:
 * - both modes see the same roots, which is also the correctness check on rebuilding roots out of entities;
 * - multiverse bytes per root stay within a per-shape bound of classic bytes per root - now far below 1, where
 *   before the entry-set container the question was how little above 1 it could be kept;
 * - the sharing itself: roots per stored entry reach the minimum the shape implies;
 * - the shared [ScopeEntityRoots] table, which the per-mask numbers deliberately exclude because it is paid once
 *   for the whole project, stays below what one mask's containers cost in classic mode.
 *
 * The absolute byte numbers and the extrapolation to real project sizes are printed, not asserted: they are what
 * makes a snapshot like the one above comparable to a test run.
 */
@TestApplication
internal class ModuleScopeMemoryTest {
  @Test
  fun `flat independent modules`() {
    // IJPL-242952: a cyclic-branches checkout, thousands of modules with no dependencies at all.
    measure(
      MemoryShape(
        ProjectShape(
          id = "flat-independent",
          moduleCount = 500 * SCALE,
          sourceRootsAt = { 1 },
        ),
        minRootsPerEntity = 1.0,
        // Measured 1.00: with one source root per module and no dependencies there is exactly one entity per root,
        // so the entry-set container stores the same map the classic one does - and the project additionally pays
        // for the shared root table. This is the shape where the change buys nothing, by construction.
        maxMultiverseOverhead = 1.05,
      )
    )
  }

  @Test
  fun `jps monorepo`() {
    // IJPL-252901: the monorepo as JPS, 6894 modules, ~88 roots per module-with-libraries scope, roots ~= entries.
    measure(
      MemoryShape(
        ProjectShape(
          id = "jps-monorepo",
          moduleCount = 300 * SCALE,
          sourceRootsAt = { 2 },
          layerSize = 40,
          exportedDepsPerModule = 2,
          ownLibraryRoots = 1,
          sdkRootCount = 3,
        ),
        minRootsPerEntity = 1.2,
        // The shape with least to share: ~1.6 roots behind an entity, so an entry replaces not much more than one
        // root slot. Measured 0.77 on the runtime mask and 0.83 on the source-only ones, where a container holds a
        // module and its two source roots. It was 1.85 when the container stored a descriptor index per root.
        maxMultiverseOverhead = 0.9,
      )
    )
  }

  @Test
  fun `bazel fine grained`() {
    // IJPL-255937: the monorepo with Bazel. Few order entries per container, but tens to thousands of source roots
    // behind a single one, and the mega-modules sit low in the graph so nearly every container pulls them in.
    measure(
      MemoryShape(
        ProjectShape(
          id = "bazel-fine-grained",
          moduleCount = 300,
          sourceRootsAt = { index ->
            when {
              index < 4 -> 400    // the `up to 10677 source roots in one module` tail of the real project
              index % 7 == 0 -> 50
              index % 3 == 0 -> 4
              else -> 1
            }
          },
          layerSize = 10,
          exportedDepsPerModule = 5,
          ownLibraryRoots = 1,
          sdkRootCount = 69,      // measured: the JdkOrderEntry contributes 69 roots to every container
        ),
        // Calibrated to the real project, where roots per order entry runs from 7.8 to 18.5 per module.
        minRootsPerEntity = 8.0,
        // Measured 0.075 on the runtime mask - 1.04 B/root against 13.90 - because 2300 roots per container hide
        // behind 148 entities. The bound is set by the two source-only masks instead, at 0.23: there a container is
        // one module with its 14 source roots, so one entry replaces 14 slots and no more. It was 1.09 when the
        // container stored a packed int per root, and 1.59 when it stored a long.
        maxMultiverseOverhead = 0.3,
      )
    )
  }

  @Test
  fun `shared big classpath`() {
    // The deduplication question raised on IJPL-255937: every module sees the same big library set and the same SDK,
    // so all containers hold byte-identical root sets and nothing is shared between them.
    measure(
      MemoryShape(
        ProjectShape(
          id = "shared-big-classpath",
          moduleCount = 200 * SCALE,
          sourceRootsAt = { 1 },
          sharedLibraries = 60,
          rootsPerSharedLibrary = 10,
          sdkRootCount = 69,
        ),
        minRootsPerEntity = 8.0,
        // Measured 0.13 on the runtime mask: 670 roots per container behind 62 entities, which is what sharing one
        // big classpath looks like from the scope cache. The bound is set by the three source-only masks, where a
        // container holds a single module with a single root and so cannot do better than break even: 1.00.
        maxMultiverseOverhead = 1.05,
      )
    )
  }

  private fun measure(shape: MemoryShape) {
    val classic = runShape(shape, multiverse = false)
    val multiverse = runShape(shape, multiverse = true)

    report(shape, classic, multiverse)

    for ((maskName, _) in MASKS) {
      val c = classic.getValue(maskName)
      val m = multiverse.getValue(maskName)
      if (c.roots == 0L) continue

      assertTrue(m.roots == c.roots) {
        "${shape.id}/$maskName: the two modes must see the same roots, got ${m.roots} vs ${c.roots}"
      }
      assertTrue(m.bytesPerRoot <= c.bytesPerRoot * shape.maxMultiverseOverhead) {
        "${shape.id}/$maskName: multiverse costs ${"%.2f".format(m.bytesPerRoot)} B/root, " +
        "classic ${"%.2f".format(c.bytesPerRoot)} B/root, " +
        "a factor of ${"%.2f".format(m.bytesPerRoot / c.bytesPerRoot)} - " +
        "more than the ${shape.maxMultiverseOverhead}x this shape allows (IJPL-255937)"
      }
      assertTrue(m.entities <= m.roots) {
        "${shape.id}/$maskName: ${m.entities} stored entries for ${m.roots} roots - the sharing is broken"
      }
    }

    // The sharing factor is the whole point of the entry-set container, so pin it per shape rather than trusting
    // the byte total: a container that kept one entry per root would still pass a loose byte bound.
    val runtime = multiverse.getValue(RUNTIME_MASK_NAME)
    assertTrue(runtime.rootsPerEntity >= shape.minRootsPerEntity) {
      "${shape.id}: ${"%.1f".format(runtime.rootsPerEntity)} roots per stored entry, " +
      "expected at least ${shape.minRootsPerEntity}"
    }

    // The table the containers stopped storing has to be paid for somewhere, and it is: once per project, against
    // every mask and every container that shares it. So the comparison is with what all the measured masks cost in
    // classic mode, not with one of them - on the flat shape, where no two containers share anything, one mask's
    // containers are smaller than the table that serves all four.
    val classicBytes = classic.values.sumOf { it.bytes }
    assertTrue(multiverse.sharedTableBytes <= classicBytes) {
      "${shape.id}: the shared root table costs ${multiverse.sharedTableBytes} B, more than the $classicBytes B " +
      "all the classic containers of this project cost"
    }
  }

  private fun runShape(shape: MemoryShape, multiverse: Boolean): Measurements =
    // The project setup, including the registry flag that decides which container is built, is shared with
    // ModuleScopeLookupBenchmarkTest: see ModuleScopeTestProject.kt.
    withScopeProject(shape.shape, multiverse) { project -> warmUpAndMeasure(project, multiverse) }

  private fun warmUpAndMeasure(project: Project, multiverse: Boolean): Measurements {
    val modules = ModuleManager.getInstance(project).modules
    val result = LinkedHashMap<String, Measurement>()

    for ((maskName, scopeOf) in MASKS) {
      val heapBefore = usedHeap()
      var roots = 0L
      var entities = 0L
      var bytes = 0L
      var bytesOnWideRefs = 0L
      for (module in modules) {
        val scope = scopeOf(module) as RootContainerScope
        assertEquals(expectedContainerClass(multiverse), scope.rootContainer.javaClass) {
          "$maskName of ${module.name} is backed by the wrong container"
        }
        val counters = scope.rootContainerCounters()
        roots += counters.rootCount
        entities += counters.entityCount
        bytes += containerBytes(counters, multiverse)
        bytesOnWideRefs += containerBytes(counters, multiverse, refBytes = 8)
      }
      val heapAfter = usedHeap()
      result[maskName] = Measurement(
        containers = modules.size,
        roots = roots,
        entities = entities,
        bytes = bytes,
        bytesOnWideRefs = bytesOnWideRefs,
        heapDelta = heapAfter - heapBefore,
      )
    }
    return Measurements(result, if (multiverse) sharedTableBytes(project) else 0L)
  }

  private fun report(shape: MemoryShape, classic: Measurements, multiverse: Measurements) {
    val out = StringBuilder()
    out.append("\n=== ").append(shape.id).append(" (").append(shape.moduleCount).append(" modules) ===\n")
    out.append("%-20s %-11s %10s %12s %12s %9s %9s %12s %8s %9s\n".format(
      "mask", "mode", "containers", "roots", "entries", "roots/c", "roots/e", "bytes", "B/root", "B/container"))
    for ((maskName, _) in MASKS) {
      for ((mode, measurements) in listOf("classic" to classic, "multiverse" to multiverse)) {
        val m = measurements.getValue(maskName)
        out.append("%-20s %-11s %10d %12d %12d %9.1f %9.1f %12d %8.2f %9.1f\n".format(
          maskName, mode, m.containers, m.roots, m.entities,
          m.rootsPerContainer, m.rootsPerEntity, m.bytes, m.bytesPerRoot, m.bytesPerContainer))
      }
    }
    // Make the numbers comparable to the snapshot in IJPL-255937. The snapshot was taken on a heap over 32 GiB, so
    // the comparison has to use the 8-byte-reference model, not whatever this JVM happens to run with.
    val runtimeMode = multiverse.getValue(RUNTIME_MASK_NAME)
    out.append("extrapolating the runtime mask at %.2f B/root (this JVM, %d-byte refs): JPS monorepo %,d roots = %,.2f GiB\n"
                 .format(runtimeMode.bytesPerRoot, REF_BYTES, JPS_PROJECT_ROOTS,
                         runtimeMode.bytesPerRoot * JPS_PROJECT_ROOTS / GIB))
    out.append("extrapolating at %.2f B/root (8-byte refs, as in the reported snapshot): Bazel monorepo %,d roots = %,.2f GiB; the snapshot measured %.2f GiB\n"
                 .format(runtimeMode.bytesPerRootOnWideRefs, BAZEL_PROJECT_ROOTS,
                         runtimeMode.bytesPerRootOnWideRefs * BAZEL_PROJECT_ROOTS / GIB, SNAPSHOT_MEASURED_GIB))
    out.append("shared ScopeEntityRoots table (once per project, excluded from the per-mask rows above): %,d B\n"
                 .format(multiverse.sharedTableBytes))
    out.append("heap delta while warming (logged only, polluted by VFS and workspace model): classic ")
      .append(classic.values.sumOf { it.heapDelta } / 1024 / 1024).append(" MB, multiverse ")
      .append(multiverse.values.sumOf { it.heapDelta } / 1024 / 1024).append(" MB\n")
    println(out)
  }

  private fun usedHeap(): Long {
    GCUtil.tryGcSoftlyReachableObjects()
    System.gc()
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
  }
}

/**
 * A [ProjectShape] plus what this test expects of it: the sharing the entry-set container must reach on that
 * shape, and how much of the classic per-root cost it may still pay. Both are properties of the shape rather
 * than of the code, which is why they live here and not in the shared shape definition.
 */
private class MemoryShape(
  val shape: ProjectShape,
  /**
   * The sharing factor the shape must reach on the runtime mask, i.e. roots per stored entry. One entry is kept per
   * distinct module, library or SDK the walk reaches, so this is `roots / entities`: 7.8 to 18.5 in the real Bazel
   * project if every order entry had a distinct owner, and more where several entries share one. Asserting it
   * separately from the byte total matters, because a container that regressed to one entry per root would still
   * pass a loose byte bound.
   */
  val minRootsPerEntity: Double,
  /**
   * What fraction of the classic per-root cost the multiverse container may reach, for this shape. Below 1 since
   * the entry-set container stores entities rather than roots; how far below is a property of the shape, because
   * the entry is amortised over [minRootsPerEntity] roots. Each bound is the measured value plus headroom.
   */
  val maxMultiverseOverhead: Double,
) {
  val id: String get() = shape.id
  val moduleCount: Int get() = shape.moduleCount
}

/**
 * One project's worth of measurements: the per-mask numbers, which are what a cached scope costs, and the shared
 * [ScopeEntityRoots] table, which is paid once for the project and so belongs to none of the masks.
 */
private class Measurements(
  private val byMask: Map<String, Measurement>,
  val sharedTableBytes: Long,
) {
  fun getValue(maskName: String): Measurement = byMask.getValue(maskName)

  val values: Collection<Measurement> get() = byMask.values
}

private class Measurement(
  val containers: Int,
  val roots: Long,
  /** Entries the containers actually store: entities for the entry-set container, nothing for the classic one. */
  val entities: Long,
  val bytes: Long,
  /** The same model with 8-byte references, i.e. the heap over 32 GiB where the problem was actually reported. */
  val bytesOnWideRefs: Long,
  val heapDelta: Long,
) {
  val rootsPerContainer: Double get() = if (containers == 0) 0.0 else roots.toDouble() / containers
  val rootsPerEntity: Double get() = if (entities == 0L) 0.0 else roots.toDouble() / entities
  val bytesPerRoot: Double get() = if (roots == 0L) 0.0 else bytes.toDouble() / roots

  val bytesPerRootOnWideRefs: Double get() = if (roots == 0L) 0.0 else bytesOnWideRefs.toDouble() / roots

  /**
   * Bytes per container, which is what matters when the containers are tiny: a one-root container still pays for the
   * map object and three array headers, so its bytes-per-root looks alarming while its total is negligible.
   */
  val bytesPerContainer: Double get() = if (containers == 0) 0.0 else bytes.toDouble() / containers
}

/** Scales the module counts of the cheap shapes; the assertions are on ratios, so they do not move with it. */
private const val SCALE = 1

// The option masks of the scopes under measurement, as computed by [ModuleScopeProviderImpl].
private const val MODULE_SCOPE_MASK = COMPILE_ONLY or TESTS
private const val WITH_DEPENDENCIES_MASK = COMPILE_ONLY or TESTS or MODULES
private const val RUNTIME_MASK = MODULES or LIBRARIES

private const val RUNTIME_MASK_NAME = "runtime($RUNTIME_MASK)"

/**
 * The scopes the tickets implicate, reached through the same public API production code uses, and named after the
 * option mask behind each. `runtime` is the mask of the 9.5 GB of `myOptions == 6` scopes in the IJPL-255937 snapshot.
 */
private val MASKS: List<Pair<String, (Module) -> GlobalSearchScope>> = listOf(
  "productionSource(0)" to { module: Module -> module.moduleProductionSourceScope },
  "moduleScope($MODULE_SCOPE_MASK)" to { module: Module -> module.moduleScope },
  "withDependencies($WITH_DEPENDENCIES_MASK)" to { module: Module -> module.moduleWithDependenciesScope },
  RUNTIME_MASK_NAME to { module: Module -> module.getModuleRuntimeScope(false) },
)

/** IJPL-252901: 6894 modules averaging 87.9 roots per module-with-libraries scope. */
private const val JPS_PROJECT_ROOTS = 606_000L

/**
 * Measured in the snapshot attached to IJPL-255937: 109331 cached containers holding 562191426 roots in
 * 1089361913 map slots - about 5100 roots per container, and three cached masks per module.
 */
private const val BAZEL_PROJECT_ROOTS = 562_191_426L

/**
 * What those containers actually cost in that snapshot, at 31.63 B/root: 1089361913 map slots (16.23 GiB, 98% of it)
 * plus 0.23 GiB of interned descriptors. See `docs/IntelliJ-Platform/4_man/Lang/Shared-Sources/Module-Scope-Memory.md`.
 */
private const val SNAPSHOT_MEASURED_GIB = 16.56

private const val GIB = 1024.0 * 1024 * 1024

/**
 * Whether the test JVM compresses references. It matters: the heap where the problem was reported is well over 32 GiB,
 * so there the slots are 8 bytes wider per root than on a default CI JVM.
 */
private val COMPRESSED_OOPS: Boolean = runCatching {
  ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean::class.java)
    .getVMOption("UseCompressedOops").value.toBoolean()
}.getOrDefault(true)

private val REF_BYTES = if (COMPRESSED_OOPS) 4L else 8L

private fun align8(bytes: Long): Long = (bytes + 7) / 8 * 8

/**
 * Bytes the container itself owns, excluding the [VirtualFile] keys - those belong to the VFS and would be alive
 * without any scope cache; the question here is what caching a root costs.
 *
 * Both containers keep a trimmed fastutil open-addressing map. fastutil marks empty slots with a `null` key rather
 * than a side table, so a slot is exactly one key reference plus one value, and there is no third array. Verified
 * against the reported snapshot, where `Object2LongOpenHashMap` declares `key`, `value`, `entries`, `keys`, `values`
 * and no `used`. The value is an `int` in both modes: a root priority for the classic container, a priority and a
 * root filter sharing one `int` for the entry-set one.
 *
 * What differs is what the map is keyed by: roots in classic mode, owning modules, libraries and SDKs in
 * multiverse mode. So the slot count is derived from the root count in one case and from the entity count in the
 * other - that difference *is* the change being measured. A key costs nothing beyond its reference in either mode:
 * a root is a [VirtualFile] the VFS owns, an entity is a module, library or SDK the project model owns.
 *
 * The table is a power of two sized for a 0.75 load factor, so a trimmed map holds roughly 1.94 slots per key and
 * up to 2.67 just past a power of two: the capacity has to be derived, not assumed to be the size.
 */
private fun containerBytes(counters: RootContainerCounters, multiverse: Boolean, refBytes: Long = REF_BYTES): Long {
  val keys = if (multiverse) counters.entityCount else counters.rootCount
  val slots = HashCommon.arraySize(keys, 0.75f).toLong() + 1
  val valueBytes = counters.entryValueBytes.toLong()
  // Mark word plus class word; the class word is 4 bytes only when references are compressed. An array header adds
  // the 4-byte length.
  val objectHeader = if (refBytes == 4L) 12L else 16L
  val arrayHeader = align8(objectHeader + 4)
  return 2 * objectHeader +                                    // the container and its map
         2 * arrayHeader +                                     // key[] and value[]
         slots * (refBytes + valueBytes)
}

/**
 * Bytes of the shared [ScopeEntityRoots] table: per entity, the [VirtualFile] array of its roots and the
 * root -> index map that makes `compare` O(1), both sized by that entity's root count rather than by how many
 * scopes reach it. This is the price of the entry-set container, and it is paid once for the whole project.
 */
private fun sharedTableBytes(project: Project, refBytes: Long = REF_BYTES): Long {
  val objectHeader = if (refBytes == 4L) 12L else 16L
  val arrayHeader = align8(objectHeader + 4)
  var bytes = 0L
  for (rootCount in ScopeEntityRoots.getInstance(project).cachedRootCounts()) {
    val slots = HashCommon.arraySize(rootCount, 0.75f).toLong() + 1
    bytes += align8(objectHeader + 3 * refBytes) +             // the EntityRootOrder
             arrayHeader + rootCount * refBytes +              // its roots array
             objectHeader + 2 * arrayHeader +                  // its index map
             slots * (refBytes + Int.SIZE_BYTES) +
             align8(objectHeader + 4 * refBytes)               // the ConcurrentHashMap node holding it
  }
  return bytes
}
