// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

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
 */
internal class DevDistResidueDirectionTest {
  @Test
  fun `a row that only enters is written`() {
    val change = divergence(
      before = residue("  vetoed_members:", "  - \"intellij.member\""),
      after = residue("  vetoed_members:", "  - \"intellij.member\"", "  - \"intellij.second\""),
    )

    // The new row rests on an entry a build really packed, and reading more products can only add such rows.
    assertTrue(residueChangeAddsOnly(change))
  }

  @Test
  fun `a row that leaves is held back`() {
    val change = divergence(
      before = residue("  vetoed_members:", "  - \"intellij.member\"", "  - \"intellij.second\""),
      after = residue("  vetoed_members:", "  - \"intellij.member\""),
    )

    // "No report needs this row" is a claim about every product, and a product this read never saw can need it.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a change that both adds and removes is held back`() {
    val change = divergence(
      before = residue("  vetoed_members:", "  - \"intellij.member\""),
      after = residue("  raw_members:", "  - \"intellij.member\""),
    )

    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a member that moves between two fields is held back`() {
    val change = divergence(
      before = residue(
        "  extra_members:", "  - \"intellij.first\"", "  - \"intellij.second\"",
        "  raw_members:", "  - \"intellij.third\"",
      ),
      after = residue(
        "  extra_members:", "  - \"intellij.first\"",
        "  raw_members:", "  - \"intellij.second\"", "  - \"intellij.third\"",
      ),
    )

    // Both fields survive and every name still stands somewhere, so only the field of a row tells this from no change
    // at all. The move takes a member out of `extra_members`, which is a removal.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `two libraries that swap their owning modules are held back`() {
    val change = divergence(
      before = residue(
        "  libraries:",
        "  - name: \"alpha\"",
        "    module: \"intellij.first\"",
        "  - name: \"beta\"",
        "    module: \"intellij.second\"",
      ),
      after = residue(
        "  libraries:",
        "  - name: \"alpha\"",
        "    module: \"intellij.second\"",
        "  - name: \"beta\"",
        "    module: \"intellij.first\"",
      ),
    )

    // A `libraries` row spans two lines, and the pair is the decision. Read as two rows, the swap leaves the row set
    // exactly as it was, and a partial read would apply it.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a merged library that moves to another member is held back`() {
    val change = divergence(
      before = residue(
        "  merged_libraries:",
        "    \"intellij.first\":",
        "    - \"alpha\"",
        "    \"intellij.second\":",
        "    - \"beta\"",
      ),
      after = residue(
        "  merged_libraries:",
        "    \"intellij.first\":",
        "    - \"beta\"",
        "    \"intellij.second\":",
        "    - \"alpha\"",
      ),
    )

    // `merged_libraries` is the one field that nests, so the member key belongs to the row. Without it both files
    // state the same two library names and the move reads as no change.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a merged member that loses one library is held back`() {
    val change = divergence(
      before = residue("  merged_libraries:", "    \"intellij.member\":", "    - \"alpha\"", "    - \"beta\""),
      after = residue("  merged_libraries:", "    \"intellij.member\":", "    - \"alpha\""),
    )

    // The whole set states the jar, so a library that leaves says the layout stopped merging it. A product this read
    // never saw can still merge it.
    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a plugin that gets its first file is written`() {
    val change = divergence(before = null, after = residue("  vetoed_members:", "  - \"intellij.member\""))

    assertTrue(residueChangeAddsOnly(change))
  }

  @Test
  fun `a file that has to go is held back`() {
    val change = divergence(before = residue("  vetoed_members:", "  - \"intellij.member\""), after = null)

    assertFalse(residueChangeAddsOnly(change))
  }

  @Test
  fun `a comment the header changes is no addition of its own`() {
    val change = divergence(
      before = "# one comment\ncontent:\n  vetoed_members:\n  - \"intellij.member\"\n",
      after = "# another comment\ncontent:\n  vetoed_members:\n  - \"intellij.member\"\n",
    )

    // Every file carries the same header, and a comment states no `PluginLayout` decision. So the rows decide, and this
    // change keeps all of them.
    assertTrue(residueChangeAddsOnly(change))
  }

  private fun divergence(before: String?, after: String?): DevDistResidueDivergence {
    return DevDistResidueDivergence(
      mainModule = "intellij.example.plugin",
      // The rule reads the two texts alone, so no file has to exist.
      file = Path.of("dev-dist.yaml"),
      before = before,
      after = after,
    )
  }

  /** One residue file, with the comment lines a real file carries around its rows. */
  private fun residue(vararg rows: String): String {
    return rows.joinToString(prefix = "# Generated - do not edit.\ncontent:\n", separator = "\n", postfix = "\n")
  }
}
