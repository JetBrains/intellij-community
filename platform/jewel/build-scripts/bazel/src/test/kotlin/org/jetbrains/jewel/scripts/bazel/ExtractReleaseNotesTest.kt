package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

private fun prViewJson(url: String, body: String, title: String): String =
    JsonObject(mapOf("url" to JsonPrimitive(url), "body" to JsonPrimitive(body), "title" to JsonPrimitive(title)))
        .toString()

class ExtractReleaseNotesTest {
    @Test
    fun `getIndentation returns the count of leading whitespace characters`() {
        assertEquals(0, getIndentation("no indent"))
        assertEquals(3, getIndentation("   indented"))
        assertEquals(0, getIndentation(""))
    }

    @Test
    fun `cleanupEntry removes a trailing dot and trims`() {
        assertEquals("Some fix", " Some fix.".cleanupEntry(null))
    }

    @Test
    fun `cleanupEntry removes a plain issue id prefix`() {
        assertEquals("Some fix", "JEWEL-100 Some fix.".cleanupEntry("JEWEL-100"))
    }

    @Test
    fun `cleanupEntry removes a bold issue id prefix`() {
        assertEquals("Some fix", "**JEWEL-100** Some fix.".cleanupEntry("JEWEL-100"))
    }

    @Test
    fun `formatReleaseNotesLine renders the issue id in bold when present`() {
        val note = ReleaseNoteItem("JEWEL-100", "Fixed the thing", "42", "https://github.com/x/y/pull/42")

        assertEquals(
            " * **JEWEL-100** Fixed the thing ([#42](https://github.com/x/y/pull/42))",
            formatReleaseNotesLine(note),
        )
    }

    @Test
    fun `formatReleaseNotesLine omits the issue id marker when absent`() {
        val note = ReleaseNoteItem(null, "Fixed the thing", "42", "https://github.com/x/y/pull/42")

        assertEquals(" * Fixed the thing ([#42](https://github.com/x/y/pull/42))", formatReleaseNotesLine(note))
    }

    @Test
    fun `formatReleaseNotesLine appends non-blank continuation lines after the first`() {
        val note = ReleaseNoteItem(null, "Fixed the thing\n  with extra detail", "42", "url")

        assertEquals(" * Fixed the thing ([#42](url))\n  with extra detail", formatReleaseNotesLine(note))
    }

    @Test
    fun `formatReleaseNotesLine omits continuation lines that are all blank`() {
        val note = ReleaseNoteItem(null, "Fixed the thing\n\n", "42", "url")

        assertEquals(" * Fixed the thing ([#42](url))", formatReleaseNotesLine(note))
    }

    @Test
    fun `processLine on a header line updates the current section`() {
        val notes = mutableMapOf<String, MutableList<ReleaseNoteItem>>()

        val (nextIndex, nextSection) = processLine(0, listOf("## New features"), "Other", null, "42", "url", notes)

        assertEquals(1, nextIndex)
        assertEquals("New features", nextSection)
        assertTrue(notes.isEmpty())
    }

    @Test
    fun `processLine on a blank line skips it and keeps the current section`() {
        val notes = mutableMapOf<String, MutableList<ReleaseNoteItem>>()

        val (nextIndex, nextSection) = processLine(0, listOf(""), "Other", null, "42", "url", notes)

        assertEquals(1, nextIndex)
        assertEquals("Other", nextSection)
    }

    @Test
    fun `processLine on a single-line list item records one note under the current section`() {
        val notes = mutableMapOf<String, MutableList<ReleaseNoteItem>>()
        val lines = listOf("* First item", "* Second item")

        val (nextIndex, _) = processLine(0, lines, "Other", "JEWEL-123", "42", "url", notes)

        assertEquals(1, nextIndex)
        assertEquals(1, nextIndex)
        assertEquals(listOf(ReleaseNoteItem("JEWEL-123", "First item", "42", "url")), notes.getValue("Other"))
    }

    @Test
    fun `processLine also recognizes dash bullets as list items`() {
        val notes = mutableMapOf<String, MutableList<ReleaseNoteItem>>()

        processLine(0, listOf("- Dash item"), "Other", null, "42", "url", notes)

        assertEquals("Dash item", notes["Other"]!!.single().description)
    }

    @Test
    fun `processLine folds indented continuation lines into the same note`() {
        val notes = mutableMapOf<String, MutableList<ReleaseNoteItem>>()
        val lines = listOf("* First item", "  continuation", "* Second item")

        val (nextIndex, _) = processLine(0, lines, "Other", null, "42", "url", notes)

        assertEquals(2, nextIndex)
        assertEquals("First item\n  continuation", notes["Other"]!!.single().description)
    }

    @Test
    fun `processLine stops a list item at the next header`() {
        val notes = mutableMapOf<String, MutableList<ReleaseNoteItem>>()
        val lines = listOf("* First item", "## New section")

        val (nextIndex, _) = processLine(0, lines, "Other", null, "42", "url", notes)

        assertEquals(1, nextIndex)
    }

    @Test
    fun `processLine skips a line that is neither blank, a header, nor a list item`() {
        val notes = mutableMapOf<String, MutableList<ReleaseNoteItem>>()

        val (nextIndex, nextSection) =
            processLine(0, listOf("Just prose, not a list item."), "Other", null, "42", "url", notes)

        assertEquals(1, nextIndex)
        assertEquals("Other", nextSection)
        assertTrue(notes.isEmpty())
    }

    @Test
    fun `processPr extracts release notes from the PR body`() = runTest {
        val body =
            """
            Some description here, unrelated to release notes.

            ## Release Notes

            * Fixed the first thing
            * Fixed the second thing

            ## Testing
            Not part of release notes.
            """
                .trimIndent()
        val json = prViewJson("https://github.com/x/y/pull/42", body, "My PR title")
        val runner = FakeCommandRunner { CmdResult.Success(json) }

        val result = processPr(CommitInfo("abc123", "42", "JEWEL-100"), isVerbose = false, File("."), runner)

        assertEquals(PrProcessingStatus.Extracted, result.status)
        assertEquals("My PR title", result.prTitle)
        assertEquals(
            listOf(
                ReleaseNoteItem("JEWEL-100", "Fixed the first thing", "42", "https://github.com/x/y/pull/42"),
                ReleaseNoteItem("JEWEL-100", "Fixed the second thing", "42", "https://github.com/x/y/pull/42"),
            ),
            result.notes.getValue("Other"),
        )
    }

    @Test
    fun `processPr matches the release notes header case-insensitively`() = runTest {
        val body = "## release notes\n\n* A fix"
        val json = prViewJson("url", body, "title")
        val runner = FakeCommandRunner { CmdResult.Success(json) }

        val result = processPr(CommitInfo("abc123", "42", null), isVerbose = false, File("."), runner)

        assertEquals(PrProcessingStatus.Extracted, result.status)
    }

    @Test
    fun `processPr reports NoReleaseNotes when no release notes header is found`() = runTest {
        val json = prViewJson("url", "Just a description, no headers at all.", "title")
        val runner = FakeCommandRunner { CmdResult.Success(json) }

        val result = processPr(CommitInfo("abc123", "42", null), isVerbose = false, File("."), runner)

        assertEquals(PrProcessingStatus.NoReleaseNotes, result.status)
    }

    @Test
    fun `processPr reports BlankReleaseNotes when the section has no content`() = runTest {
        val body = "## Release Notes\n\n\n## Testing\nSomething"
        val json = prViewJson("url", body, "title")
        val runner = FakeCommandRunner { CmdResult.Success(json) }

        val result = processPr(CommitInfo("abc123", "42", null), isVerbose = false, File("."), runner)

        assertEquals(PrProcessingStatus.BlankReleaseNotes, result.status)
    }

    @Test
    fun `processPr truncates the body at the CURSOR_SUMMARY marker before looking for release notes`() = runTest {
        val body = "No release notes here.\n<!-- CURSOR_SUMMARY -->\n## Release Notes\n\n* Hidden fix"
        val json = prViewJson("url", body, "title")
        val runner = FakeCommandRunner { CmdResult.Success(json) }

        val result = processPr(CommitInfo("abc123", "42", null), isVerbose = false, File("."), runner)

        assertEquals(PrProcessingStatus.NoReleaseNotes, result.status)
    }

    @Test
    fun `processPr reports Error status when the gh output is not valid JSON`() = runTest {
        val runner = FakeCommandRunner { CmdResult.Success("not json") }

        val result = processPr(CommitInfo("abc123", "42", null), isVerbose = false, File("."), runner)

        assertEquals(PrProcessingStatus.Error, result.status)
        assertEquals("[ERROR]", result.prTitle)
    }
}
