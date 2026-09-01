// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which residue change a partial read may write, which is the direction rule of [residueChangeAddsOnly].
 *
 * The rule is what keeps a run given some of the products honest. A report set that covers 496 of the 516 plugins the
 * population names is the best this host can produce, because the packaging suites here build no CLion, no RustRover and
 * no Gateway. So the gate has to stay usable on a partial read, and it may still not act on a row whose absence it cannot
 * establish.
 *
 * Measured on this tree: IDEA Ultimate's report alone asks to drop 8 plugins' `vetoed_members` and `raw_members` rows,
 * and the union of seven products keeps every one. Case [`a row that leaves is held back`] is that case in the small.
 *
 * Every case states a [ContentResidueSection] and lets [contentResidueRows] make the rows, so a case measures the rule
 * and never a text format. The rows are what the rule reads, and what the central table writes.
 */
internal class DevDistResidueDirectionTest {
  @Test
  fun `a row that only enters is written`() {
    val change = divergence(
      before = ContentResidueSection(vetoedMembers = listOf("intellij.member")),
      after = ContentResidueSection(vetoedMembers = listOf("intellij.member", "intellij.second")),
    )

    // The new row rests on an entry a build really packed, and reading more products can only add such rows.
    assertTrue(residueChangeAddsOnly(change))
  }

  @Test
  fun `a row that leaves is held back`() {
    val change = divergence(
      before = ContentResidueSection(vetoedMembers = listOf("intellij.member", "intellij.second")),
      after = ContentResidueSection(vetoedMembers = listOf("intellij.member")),
    )

    // "No report needs this row" is a claim about every product, and a product this read never saw can need it.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a change that both adds and removes is held back`() {
    val change = divergence(
      before = ContentResidueSection(vetoedMembers = listOf("intellij.member")),
      after = ContentResidueSection(rawMembers = listOf("intellij.member")),
    )

    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a member that moves between two fields is held back`() {
    val change = divergence(
      before = ContentResidueSection(
        separateJars = listOf("intellij.first", "intellij.second"),
        rawMembers = listOf("intellij.third"),
      ),
      after = ContentResidueSection(
        separateJars = listOf("intellij.first"),
        rawMembers = listOf("intellij.second", "intellij.third"),
      ),
    )

    // Both fields survive and every name still stands somewhere, so only the field of a row tells this from no change
    // at all. The move takes a member out of `separate_jars`, which is a removal.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `two libraries that swap their owning modules are held back`() {
    val change = divergence(
      before = ContentResidueSection(
        libraries = listOf(
          ResidueLibraryRow(module = "intellij.first", name = "alpha"),
          ResidueLibraryRow(module = "intellij.second", name = "beta"),
        ),
      ),
      after = ContentResidueSection(
        libraries = listOf(
          ResidueLibraryRow(module = "intellij.first", name = "beta"),
          ResidueLibraryRow(module = "intellij.second", name = "alpha"),
        ),
      ),
    )

    // A `module_library` row carries the owning module and the library together, because the pair is the decision. Two
    // rows of one token each would leave the row set exactly as it was, and a partial read would apply the swap.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a project library that gains an owning module is held back`() {
    val change = divergence(
      before = ContentResidueSection(libraries = listOf(ResidueLibraryRow(name = "alpha"))),
      after = ContentResidueSection(libraries = listOf(ResidueLibraryRow(module = "intellij.owner", name = "alpha"))),
    )

    // The two kinds take two field names, so the change is a `project_library` row leaving and a `module_library` row
    // entering. One field for both would read the owner as an addition to an unchanged row.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a merged library that moves to another member is held back`() {
    val change = divergence(
      before = ContentResidueSection(
        mergedLibraries = mapOf("intellij.first" to listOf("alpha"), "intellij.second" to listOf("beta")),
      ),
      after = ContentResidueSection(
        mergedLibraries = mapOf("intellij.first" to listOf("beta"), "intellij.second" to listOf("alpha")),
      ),
    )

    // A `merged_library` row states the member and the library, because the member key belongs to the decision. Without
    // it both sides state the same two library names and the move reads as no change.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a merged member that loses one library is held back`() {
    val change = divergence(
      before = ContentResidueSection(mergedLibraries = mapOf("intellij.member" to listOf("alpha", "beta"))),
      after = ContentResidueSection(mergedLibraries = mapOf("intellij.member" to listOf("alpha"))),
    )

    // The whole set states the jar, so a library that leaves says the layout stopped merging it. A product this read
    // never saw can still merge it.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a merged member that loses its last library is held back`() {
    val change = divergence(
      before = ContentResidueSection(mergedLibraries = mapOf("intellij.member" to listOf("alpha"))),
      after = ContentResidueSection(mergedLibraries = mapOf("intellij.member" to emptyList())),
    )

    // `merges_no_library` is a row of its own, so a member that empties states one row instead of losing its only one.
    // The `merged_library` row still leaves, which is what the rule holds back.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a member jars row that only enters is written`() {
    val change = divergence(
      before = ContentResidueSection(memberJars = mapOf("intellij.member" to listOf("member.jar"))),
      after = ContentResidueSection(
        memberJars = mapOf("intellij.member" to listOf("member.jar"), "intellij.second" to listOf("second.jar")),
      ),
    )

    // The new row rests on a jar a build really packed, so reading more products can only add such a row.
    assertTrue(residueChangeAddsOnly(change))
  }

  @Test
  fun `a member jar that moves to another member is held back`() {
    val change = divergence(
      before = ContentResidueSection(
        memberJars = mapOf("intellij.first" to listOf("alpha.jar"), "intellij.second" to listOf("beta.jar")),
      ),
      after = ContentResidueSection(
        memberJars = mapOf("intellij.first" to listOf("beta.jar"), "intellij.second" to listOf("alpha.jar")),
      ),
    )

    // A `member_jar` row carries the member and the jar, exactly as `merged_library` carries the member and the
    // library. Without the member both sides state the same two jar names and the move reads as no change.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a plugin that gets its first row is written`() {
    val change = divergence(before = null, after = ContentResidueSection(vetoedMembers = listOf("intellij.member")))

    assertTrue(residueChangeAddsOnly(change))
  }

  @Test
  fun `a plugin whose rows all have to go is held back`() {
    val change = divergence(before = ContentResidueSection(vetoedMembers = listOf("intellij.member")), after = null)

    assertFalse(residueChangeAddsOnly(change))
  }

  private fun divergence(before: ContentResidueSection?, after: ContentResidueSection?): DevDistResidueDivergence {
    return DevDistResidueDivergence(
      mainModule = PLUGIN,
      before = before?.let { contentResidueRows(plugin = PLUGIN, section = it) },
      after = after?.let { contentResidueRows(plugin = PLUGIN, section = it) },
    )
  }
}

private const val PLUGIN: String = "intellij.example.plugin"
