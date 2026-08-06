package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

private val testFile = File("api-dump-test.txt")

class AnnotateApiDumpChangesTest {
    private val tmpDir = createSafeTempDir("annotate-api-dump-changes-test")

    private fun apiDumpFile(dir: File, name: String = "api-dump.txt"): File =
        dir.resolve(name).also { it.writeText("") }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `processDiff with empty string should return an empty list`() {
        val result = processDiff("", testFile, true)

        assertEquals(0, result.size)
    }

    @Test
    fun `processDiff only addition diff should not raise breaking changes`() {
        val apiDiff =
            """
            --- a/additionTest.kt
            +++ b/additionTest.kt
            @@ -5,0 +6 @@
            +   println("Hi developer! This print statement was the only thing added!")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true)

        assertEquals(0, result.size)
    }

    @Test
    fun `processDiff only removal diff should raise one breaking change`() {
        val apiDiff =
            """
            --- a/removalTest.kt
            +++ b/removalTest.kt
            @@ -5 +4,0 @@
            -   println("Hi developer! This print statement was straight up removed for good.")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true)

        assertEquals(1, result.size)
    }

    @Test
    fun `processDiff only replace diff should raise one breaking change`() {
        val apiDiff =
            """
            --- a/replaceTest.kt
            +++ b/replaceTest.kt
            @@ -5 +5 @@
            -   println("Hi developer! This print statement will be replaced.")
            +   println("Hi developer! Just got replaced.") 
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true)

        assertEquals(1, result.size)
    }

    @Test
    fun `processDiff multiple removals in one file should raise the correct number of BreakingChanges`() {
        val apiDiff =
            """
            --- a/removalTest.kt
            +++ b/removalTest.kt
            @@ -5,3 +4,0 @@
            -   println("Hi developer! This line was removed.")
            -   println("This one also got removed.")
            -   println("And this one :(")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true)

        assertEquals(3, result.size)
    }

    @Test
    fun `processDiff multiple removals scattered in the same file should raise more than one BreakingChange`() {
        val apiDiff =
            """
            --- a/removalTest.kt
            +++ b/removalTest.kt
            @@ -5,3 +4,0 @@
            -   println("Hi developer! This line was removed.")
            -   println("This one also got removed.")
            -   println("And this one :(")
            @@ -10,1 +9,0 @@
            -   println("This line was removed in the same file, but later on the file.")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true)

        assertEquals(4, result.size)
    }

    @Test
    fun `processDiff multiple removals in different files should the correct number of BreakingChange`() {
        val apiDiff =
            """
            --- a/removalTest.kt
            +++ b/removalTest.kt
            @@ -5 +4,0 @@
            -   println("Hi developer! This line was removed.")
            
            --- a/removalTestTwo.kt
            +++ b/removalTestTwo.kt
            @@ -8,2 +7,0 @@
            -   println("Why are we deleting so many lines?")
            -   println("Guess this one also got deleted...")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true)

        assertEquals(3, result.size)
    }

    @Test
    fun `processDiff removals and replaces should count as breaking changes`() {
        val apiDiff =
            """
            --- a/removalTest.kt
            +++ b/removalTest.kt
            @@ -5,3 +4,0 @@
            -   println("Hi developer! This line was removed.")
            -   println("This one also got removed.")
            -   println("And this one :(")
            @@ -14 +14 @@
            -   println("This text got replaced...")
            +   println("...by this text :)")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true)

        assertEquals(4, result.size)
    }

    @Test
    fun `processDiff breaking change found should have correct data`() {
        val apiDiff =
            """
            --- a/removalTest.kt
            +++ b/removalTest.kt
            @@ -5 +4,0 @@
            -   println("Hi developer! We need to check the data in this line")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, false).first()

        val expected =
            BreakingChange(
                file = testFile,
                "   println(\"Hi developer! We need to check the data in this line\")",
                false,
                5,
                4,
                true,
            )

        assertEquals(expected, result)
    }

    @Test
    fun `processDiff consecutive removals should have annotate false if in the same hunk header`() {
        val apiDiff =
            """
            --- a/removalTest.kt
            +++ b/removalTest.kt
            @@ -5,3 +4,0 @@
            -   println("Hi developer! This line was removed.")
            -   println("This one also got removed.")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true)

        val expected =
            listOf(
                BreakingChange(
                    file = testFile,
                    "   println(\"Hi developer! This line was removed.\")",
                    true,
                    5,
                    4,
                    true,
                ),
                BreakingChange(file = testFile, "   println(\"This one also got removed.\")", true, 6, 4, false),
            )

        assertEquals(expected, result)
    }

    @Test
    fun `processDiff removals in different hunks should have annotate true`() {
        val apiDiff =
            """
            --- a/removalTest.kt
            +++ b/removalTest.kt
            @@ -5 +4,0 @@
            -   println("Hi developer! This line was removed.")
            @@ -13 +12,0 @@
            -   println("This one also got removed.")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true)

        val expected =
            listOf(
                BreakingChange(
                    file = testFile,
                    "   println(\"Hi developer! This line was removed.\")",
                    true,
                    5,
                    4,
                    true,
                ),
                BreakingChange(file = testFile, "   println(\"This one also got removed.\")", true, 13, 12, true),
            )

        assertEquals(expected, result)
    }

    @Test
    fun `processDiff removals separated by an addition should both have annotate true`() {
        val apiDiff =
            """
            --- a/removalTest.kt
            +++ b/removalTest.kt
            @@ -5,2 +5 @@
            -   println("Hi developer! This line was removed.")
            +   println("And this line was added")
            -   println("But this one also got removed.")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true)

        val expected =
            listOf(
                BreakingChange(
                    file = testFile,
                    "   println(\"Hi developer! This line was removed.\")",
                    true,
                    5,
                    5,
                    true,
                ),
                BreakingChange(file = testFile, "   println(\"But this one also got removed.\")", true, 6, 6, true),
            )

        assertEquals(expected, result)
    }

    @Test
    fun `processDiff removal with percentage symbol should be escaped`() {
        val apiDiff =
            """
            --- a/removalTest.kt
            +++ b/removalTest.kt
            @@ -5 +4,0 @@
            -   println("This line has a character '%' that should be escaped.")
            """
                .trimIndent()

        val result = processDiff(apiDiff, testFile, true).first().lineContent

        val expected = "   println(\"This line has a character '%25' that should be escaped.\")"

        assertEquals(expected, result)
    }

    @Test
    fun `validateDumps returns false when no files changed`() = runTest {
        val dir = tmpDir.resolve("no-change").also { it.mkdirs() }
        apiDumpFile(dir)
        val runner = FakeCommandRunner { CmdResult.Success("") }

        val result = validateDumps(false, false, "abc123", dir, runner) { it.name == "api-dump.txt" }

        assertFalse(result)
    }

    @Test
    fun `validateDumps returns true when breaking change found`() = runTest {
        val dir = tmpDir.resolve("with-change").also { it.mkdirs() }
        dir.resolve("api-dump.txt").also { it.writeText("") }
        val fakeDiff =
            """
            --- a/api-dump.txt
            +++ b/api-dump.txt
            @@ -5 +4,0 @@
            -   fun deletedFunction()
        """
                .trimIndent()

        val runner = FakeCommandRunner { command ->
            when {
                command.contains("--quiet") -> CmdResult.Failure("") // `isModifiedResult` fails
                else -> CmdResult.Success(fakeDiff)
            }
        }

        val result = validateDumps(false, false, "baseCommit", dir, runner) { it.name == "api-dump.txt" }

        assertTrue(result)
    }

    @Test
    fun `validateDumps breaking changes in samples folder are ignored`() = runTest {
        val samplesDir = tmpDir.resolve("samples").also { it.mkdirs() }
        samplesDir.resolve("api-dump.txt").also { it.writeText("") }
        val fakeDiff =
            """
            --- a/api-dump.txt
            +++ b/api-dump.txt
            @@ -5 +4,0 @@
            -   fun deletedFunction()
        """
                .trimIndent()

        val runner = FakeCommandRunner { command ->
            when {
                command.contains("--quiet") -> CmdResult.Failure("") // `isModifiedResult` fails
                else -> CmdResult.Success(fakeDiff)
            }
        }

        val result = validateDumps(false, false, "baseCommit", tmpDir, runner) { it.name == "api-dump.txt" }

        assertFalse(result)
    }

    @Test
    fun `validateDumps with only additions in dump should return false`() = runTest {
        val dir = tmpDir.resolve("with-change").also { it.mkdirs() }
        dir.resolve("api-dump.txt").also { it.writeText("") }
        val fakeDiff =
            """
            --- a/api-dump.txt
            +++ b/api-dump.txt
            @@ -5,0 +6 @@
            +   fun addedFunction()
        """
                .trimIndent()

        val runner = FakeCommandRunner { command ->
            when {
                command.contains("--quiet") -> CmdResult.Failure("") // `isModifiedResult` fails
                else -> CmdResult.Success(fakeDiff)
            }
        }

        val result = validateDumps(false, false, "baseCommit", dir, runner) { it.name == "api-dump.txt" }

        assertFalse(result)
    }

    @Test
    fun `validateDumps two files but only one has a breaking change should return true`() = runTest {
        val noChangeDir = tmpDir.resolve("no-change").also { it.mkdirs() }
        val withChangeDir = tmpDir.resolve("with-change").also { it.mkdirs() }
        noChangeDir.resolve("api-dump.txt").also { it.writeText("") }
        withChangeDir.resolve("api-dump.txt").also { it.writeText("") }
        val fakeDiff =
            """
            --- a/api-dump.txt
            +++ b/api-dump.txt
            @@ -5,0 +6 @@
            -   fun deletedFunction()
        """
                .trimIndent()

        val runner = FakeCommandRunner { command ->
            when {
                command.contains("--quiet") && command.contains("no-change") -> CmdResult.Success("")
                command.contains("--quiet") && command.contains("with-change") -> CmdResult.Failure("")
                else -> CmdResult.Success(fakeDiff)
            }
        }

        val result = validateDumps(false, false, "baseCommit", tmpDir, runner) { it.name == "api-dump.txt" }

        assertTrue(result)
    }

    @Test
    fun `validateDumps returns false when filter matches no files`() = runTest {
        val dir = tmpDir.resolve("with-change").also { it.mkdirs() }
        dir.resolve("api-dump.txt").also { it.writeText("") }
        val fakeDiff =
            """
            --- a/api-dump.txt
            +++ b/api-dump.txt
            @@ -5 +4,0 @@
            -   fun deletedFunction()
        """
                .trimIndent()

        val runner = FakeCommandRunner { command ->
            when {
                command.contains("--quiet") -> CmdResult.Failure("") // `isModifiedResult` fails
                else -> CmdResult.Success(fakeDiff)
            }
        }

        val result = validateDumps(false, false, "baseCommit", dir, runner) { it.name == "non-existing-file.txt" }

        assertFalse(result)
    }

    @Test
    fun `assert stable breaking change formatLog result is correct`() {
        val lineContent = "this is a breaking change"
        val oldLineNum = 0
        val newLineNum = 0
        val breakingChange =
            BreakingChange(
                file = tmpDir,
                lineContent = "this is a breaking change",
                experimental = false,
                oldLineNum = oldLineNum,
                newLineNum = newLineNum,
                annotate = true,
            )
        val log = StringBuilder()
        breakingChange.formatLog(log, tmpDir)
        val result = log.toString()

        assertContains(result, "⚠️ Breaking stable API change:\n       line $oldLineNum removed: $lineContent")
        assertContains(result, "::error")
        assertContains(result, "line=$newLineNum")
        assertContains(
            result,
            "title=Breaking API change::This looks like a breaking API change, make sure it's intended",
        )
    }

    @Test
    fun `assert experimental breaking change formatLog result is correct`() {
        val lineContent = "this is a breaking change"
        val oldLineNum = 0
        val newLineNum = 0
        val breakingChange =
            BreakingChange(
                file = tmpDir,
                lineContent = "this is a breaking change",
                experimental = true,
                oldLineNum = oldLineNum,
                newLineNum = newLineNum,
                annotate = true,
            )
        val log = StringBuilder()
        breakingChange.formatLog(log, tmpDir)
        val result = log.toString()

        assertContains(result, "⚠️ Breaking experimental API change:\n       line $oldLineNum removed: $lineContent")
        assertContains(result, "::warning")
        assertContains(result, "line=$newLineNum")
        assertContains(
            result,
            "title=Breaking experimental API change::This looks like a breaking API change, make sure it's intended",
        )
    }

    @Test
    fun `assert breaking change formatLog with annotate false only appends first line`() {
        val lineContent = "this is a breaking change"
        val oldLineNum = 0
        val newLineNum = 0
        val breakingChange =
            BreakingChange(
                file = tmpDir,
                lineContent = "this is a breaking change",
                experimental = false,
                oldLineNum = oldLineNum,
                newLineNum = newLineNum,
                annotate = false,
            )
        val log = StringBuilder()
        breakingChange.formatLog(log, tmpDir)
        val result = log.toString()

        assertContains(result, "⚠️ Breaking stable API change:\n       line $oldLineNum removed: $lineContent")
        assertFalse(result.contains("::error"))
        assertFalse(result.contains("::warning"))
    }
}
