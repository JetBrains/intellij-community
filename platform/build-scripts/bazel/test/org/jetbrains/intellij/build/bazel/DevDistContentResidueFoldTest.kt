// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Which rows one pass of [foldPluginContentResidue] keeps, which rows it replaces, and in which order it states them.
 *
 * The fold writes one central file for every plugin, and a per-plugin file got each of these rules for free. A plugin
 * the run says nothing about kept its own file because the run never opened it, and a held-back plugin kept its file
 * because the run never wrote it. One file makes each of those an explicit rule, so a rule that goes wrong drops a
 * `PluginLayout` decision from the tree in silence, and no jar is seen to be wrong until class load.
 *
 * Every case states a [ContentResidueSection] and reads the folded text back with [readPluginContentResidue], so a case
 * measures the rule and not the file format. [DevDistResidueDirectionTest] states the direction rule that decides which
 * plugin the fold is told to hold back; here that set is an input.
 */
internal class DevDistContentResidueFoldTest {
  @JvmField
  @Rule
  val temporaryFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun `a covered plugin takes the folded rows`() {
    val text = fold(
      checkedIn = mapOf(COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.stale"))),
      reported = setOf(COVERED),
      folded = mapOf(COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.fresh"))),
    )

    // A report covers the plugin and the direction rule let the change through, so the fold states the report's answer.
    assertEquals(mapOf(COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.fresh"))), read(text))
  }

  @Test
  fun `a plugin no report covers keeps every row`() {
    val kept = ContentResidueSection(
      libRootJars = listOf("intellij.at.lib.root"),
      rawMembers = listOf("intellij.raw"),
      vetoedMembers = listOf("intellij.vetoed"),
      separateJars = listOf("intellij.apart"),
      memberJars = mapOf("intellij.placed" to listOf("main.jar", "placed/placed.jar")),
      // A member that merges nothing states `merges_no_library`, which is the row a fold that drops empty values loses.
      mergedLibraries = mapOf("intellij.merging" to listOf("alpha"), "intellij.merges.nothing" to emptyList()),
      libraries = listOf(ResidueLibraryRow(name = "project-library"), ResidueLibraryRow(module = "intellij.owner", name = "owned")),
    )

    val text = fold(
      checkedIn = mapOf(UNCOVERED to kept),
      reported = setOf(COVERED),
      folded = mapOf(COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.fresh"))),
    )

    // No supplied report names this plugin, so the run knows nothing about it and every one of its seven fields stands.
    assertEquals(
      mapOf(
        COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.fresh")),
        UNCOVERED to kept,
      ),
      read(text),
    )
  }

  @Test
  fun `a covered plugin the reports need no residue for loses its rows`() {
    val text = fold(
      checkedIn = mapOf(
        COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.stale")),
        UNCOVERED to ContentResidueSection(rawMembers = listOf("intellij.raw")),
      ),
      reported = setOf(COVERED),
    )

    // The plugin is covered and the fold states no section for it, so the derivation now reproduces the report alone.
    // That is the one removal a covering read may make, and it takes this plugin's rows and no other plugin's.
    assertEquals(mapOf(UNCOVERED to ContentResidueSection(rawMembers = listOf("intellij.raw"))), read(text))
  }

  @Test
  fun `a held-back plugin keeps its checked-in rows`() {
    val text = fold(
      checkedIn = mapOf(COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.first", "intellij.second"))),
      reported = setOf(COVERED),
      folded = mapOf(COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.first"))),
      skipped = setOf(COVERED),
    )

    // The direction rule held the change back, so the fold has to write the plugin back as it stands. Writing the
    // folded section here would drop the row anyway and hide the change from the reader at the same time.
    assertEquals(
      mapOf(COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.first", "intellij.second"))),
      read(text),
    )
  }

  @Test
  fun `a held-back plugin whose rows would all go keeps them`() {
    val text = fold(
      checkedIn = mapOf(COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.member"))),
      reported = setOf(COVERED),
      skipped = setOf(COVERED),
    )

    // The plugin is covered and the fold states no section, which is the removal a partial read may not make. The
    // skipped set reaches the fold through a second route here: nothing overwrites the plugin, so the keep has to.
    assertEquals(mapOf(COVERED to ContentResidueSection(vetoedMembers = listOf("intellij.member"))), read(text))
  }

  @Test
  fun `a covered plugin new to the file gains its rows`() {
    val text = fold(
      reported = setOf(COVERED),
      folded = mapOf(COVERED to ContentResidueSection(separateJars = listOf("intellij.apart"))),
    )

    assertEquals(mapOf(COVERED to ContentResidueSection(separateJars = listOf("intellij.apart"))), read(text))
  }

  @Test
  fun `an absent file folds to the reports alone`() {
    val file = temporaryFolder.root.toPath().resolve(PLUGIN_CONTENT_RESIDUE_FILE_NAME)
    val table = foldPluginContentResidue(
      file = file,
      reported = setOf(COVERED),
      folded = mapOf(COVERED to ContentResidueSection(rawMembers = listOf("intellij.raw"))),
      skipped = emptySet(),
    )

    // The first run of a tree that has no table. The fold reads nothing rather than failing, and it hands the caller
    // the path it read, because that is the path the write goes to.
    assertEquals(file, table.file)
    assertEquals(mapOf(COVERED to ContentResidueSection(rawMembers = listOf("intellij.raw"))), read(table.text))
  }

  @Test
  fun `the file states the plugins in name order`() {
    val text = fold(
      checkedIn = mapOf(
        "intellij.d.kept" to ContentResidueSection(rawMembers = listOf("intellij.raw")),
        "intellij.b.held" to ContentResidueSection(rawMembers = listOf("intellij.raw")),
      ),
      reported = setOf("intellij.b.held", "intellij.c.folded", "intellij.a.folded"),
      folded = mapOf(
        "intellij.c.folded" to ContentResidueSection(rawMembers = listOf("intellij.raw")),
        "intellij.a.folded" to ContentResidueSection(rawMembers = listOf("intellij.raw")),
        "intellij.b.held" to ContentResidueSection(rawMembers = listOf("intellij.other")),
      ),
      skipped = setOf("intellij.b.held"),
    )

    // One order for the whole file, whichever of the three routes a plugin arrives by. Without it a regeneration would
    // move a plugin's block whenever the reports change, and the diff would state a change no row makes.
    assertEquals(
      listOf("intellij.a.folded", "intellij.b.held", "intellij.c.folded", "intellij.d.kept"),
      rowsOf(text).map { it.substringBefore('\t') },
    )
  }

  @Test
  fun `a kept plugin's rows come back sorted rather than as they were typed`() {
    val file = temporaryFolder.root.toPath().resolve(PLUGIN_CONTENT_RESIDUE_FILE_NAME)
    file.writeText(
      "$UNCOVERED\t$VETOED_MEMBER_FIELD\tintellij.second\n" +
      "$UNCOVERED\t$VETOED_MEMBER_FIELD\tintellij.first\n"
    )

    val table = foldPluginContentResidue(file = file, reported = emptySet(), folded = emptyMap(), skipped = emptySet())

    // "Kept as it stands" is the row set and not the bytes. The reader sorts every field, so a hand-edited block comes
    // back in the file's own order and no row is lost on the way.
    assertEquals(
      listOf("$UNCOVERED\t$VETOED_MEMBER_FIELD\tintellij.first", "$UNCOVERED\t$VETOED_MEMBER_FIELD\tintellij.second"),
      rowsOf(table.text),
    )
  }

  /** One fold over a table written from [checkedIn], as the text the pass would write. */
  private fun fold(
    checkedIn: Map<String, ContentResidueSection> = emptyMap(),
    reported: Set<String> = emptySet(),
    folded: Map<String, ContentResidueSection> = emptyMap(),
    skipped: Set<String> = emptySet(),
  ): String {
    val file = temporaryFolder.root.toPath().resolve(PLUGIN_CONTENT_RESIDUE_FILE_NAME)
    file.writeText(renderPluginContentResidue(checkedIn))
    return foldPluginContentResidue(file = file, reported = reported, folded = folded, skipped = skipped).text
  }

  /** The folded text, read back through the file's own reader. */
  private fun read(text: String): Map<String, ContentResidueSection> {
    val file: Path = temporaryFolder.newFile().toPath()
    file.writeText(text)
    return readPluginContentResidue(file)
  }

  /** The rows of the folded text, in the order it states them, without the header. */
  private fun rowsOf(text: String): List<String> {
    return text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
  }
}

/** A plugin a supplied report names, so the run can speak for it. */
private const val COVERED: String = "intellij.covered.plugin"

/** A plugin no supplied report names, which is what a partial read leaves alone. */
private const val UNCOVERED: String = "intellij.uncovered.plugin"
