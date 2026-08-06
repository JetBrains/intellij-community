package org.jetbrains.jewel.scripts.bazel

import com.github.ajalt.clikt.command.test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CompareBranchesTest {
    private val fakeRunner = FakeCommandRunner { CmdResult.Success("") }
    private val testClass = CompareBranchesCommand(fakeRunner, pathExists = { true })

    @Test
    fun `normalizeSubject subject with cherry pick prefix should ignore it`() {
        val subject = "cherry picked from commit 123: cool cherry-pick"

        val result = testClass.normalizeSubject(subject)

        val expected = "cool cherry-pick"

        assertEquals(expected, result)
    }

    @Test
    fun `normalizeSubject without cherry-pick prefix should be returned unchanged`() {
        val subject = "this was not a cherry-pick"

        val result = testClass.normalizeSubject(subject)

        assertEquals(subject, result)
    }

    @Test
    fun `isCommitAfterDate with valid dates and authorDate after sinceDate returns true`() {
        val authorDateStr = "2026-05-13 12:37:32 +0300"
        val sinceDateStr = "2026-05-10"

        val result = testClass.isCommitAfterDate(authorDateStr, sinceDateStr)

        assertTrue(result)
    }

    @Test
    fun `isCommitAfterDate with valid dates and authorDate equals sinceDate returns true`() {
        val authorDateStr = "2026-05-13 12:37:32 +0300"
        val sinceDateStr = "2026-05-13"

        val result = testClass.isCommitAfterDate(authorDateStr, sinceDateStr)

        assertTrue(result)
    }

    @Test
    fun `isCommitAfterDate with valid dates and authorDate before sinceDate returns false`() {
        val authorDateStr = "2026-05-13 12:37:32 +0300"
        val sinceDateStr = "2026-05-15"

        val result = testClass.isCommitAfterDate(authorDateStr, sinceDateStr)

        assertFalse(result)
    }

    @Test
    fun `isCommitAfterDate unparseable dates and authorDate after sinceDate returns true`() {
        val authorDateStr = "2026-05-13 invalid date"
        val sinceDateStr = "2026-05-10"

        val result = testClass.isCommitAfterDate(authorDateStr, sinceDateStr)

        assertTrue(result)
    }

    @Test
    fun `isCommitAfterDate unparseable dates and authorDate equals sinceDate returns true`() {
        val authorDateStr = "2026-05-13 invalid date"
        val sinceDateStr = "2026-05-13"

        val result = testClass.isCommitAfterDate(authorDateStr, sinceDateStr)

        assertTrue(result)
    }

    @Test
    fun `isCommitAfterDate unparseable dates and authorDate before sinceDate returns false`() {
        val authorDateStr = "2026-05-13 invalid date"
        val sinceDateStr = "2026-05-15"

        val result = testClass.isCommitAfterDate(authorDateStr, sinceDateStr)

        assertFalse(result)
    }

    @Test
    fun `parseCommitLine correctly parses commit line`() {
        val commitLine = "5f4c0c6c9476::COMMIT::[JEWEL-007] Casino Royale Easter Egg"

        val result = testClass.parseCommitLine(commitLine)

        val expected = CommitInfo(hash = "5f4c0c6c9476", subject = "[JEWEL-007] Casino Royale Easter Egg")

        assertEquals(expected, result)
    }

    @Test
    fun `parseCommitLine trims apostrophe from hash and subject`() {
        val commitLine = "'5f4c0c6c9476'::COMMIT::'[JEWEL-007] Casino Royale Easter Egg'"

        val result = testClass.parseCommitLine(commitLine)

        val expected = CommitInfo(hash = "5f4c0c6c9476", subject = "[JEWEL-007] Casino Royale Easter Egg")

        assertEquals(expected, result)
    }

    @Test
    fun `parseCommitWithDateResult with commit after sinceDate return ParseResult Success`() = runTest {
        val line = "408c5::COMMIT::[JEWEL-007] Casino Royale Easter Egg,2026-05-13 22:27:38 +0300"

        // initializing command with sinceDate
        testClass.test(listOf("branch1", "branch2", "--since", "2026-05-10"))
        val result = testClass.parseCommitWithDateResult(line)

        val expected = ParseResult.Success(CommitInfo(hash = "408c5", subject = "[JEWEL-007] Casino Royale Easter Egg"))

        assertEquals(expected, result)
    }

    @Test
    fun `parseCommitWithDateResult with commit before sinceDate return ParseResult DateFiltered`() = runTest {
        val line = "408c5::COMMIT::[JEWEL-007] Casino Royale Easter Egg,2026-05-13 22:27:38 +0300"

        // initializing command with sinceDate
        testClass.test(listOf("branch1", "branch2", "--since", "2026-05-15"))
        val result = testClass.parseCommitWithDateResult(line)

        assertEquals(ParseResult.DateFiltered, result)
    }

    @Test
    fun `parseCommitWithDateResult with invalid line date returns ParseError`() = runTest {
        val line = "408c5::COMMIT::[JEWEL-007] Casino Royale Easter Egg,this date is invalid"

        // initializing command with sinceDate
        testClass.test(listOf("branch1", "branch2"))
        val result = testClass.parseCommitWithDateResult(line)

        assertEquals(ParseResult.ParseError, result)
    }

    @Test
    fun `parseCommitWithDateResult commit message with surrounding quotes should remove them`() = runTest {
        val line = "408c5::COMMIT::'[JEWEL-007] Casino Royale Easter Egg',2026-05-13 10:10:10 +0300"

        // initializing command with sinceDate
        testClass.test(listOf("branch1", "branch2", "--since", "2026-05-10"))
        val result = testClass.parseCommitWithDateResult(line) as ParseResult.Success

        assertEquals("[JEWEL-007] Casino Royale Easter Egg", result.commit.subject)
    }

    @Test
    fun `fetchCommits returns all commits when all are valid`() = runTest {
        val fakeRunner = FakeCommandRunner { command ->
            when {
                command.contains("--pretty=format:") -> {
                    CmdResult.Success(
                        """
                            408c5::COMMIT::[JEWEL-007] Casino Royale Easter Egg,2026-05-13 13:13:13 +0300
                            be0b1::COMMIT::[JEWEL-101] Adding README,2026-05-13 22:27:38 +0200
                            """
                            .trimIndent()
                    )
                }
                else -> CmdResult.Success("")
            }
        }
        val testClass = CompareBranchesCommand(fakeRunner, pathExists = { true })
        testClass.test(listOf("branch1", "branch2", "--since", "2026-05-10")) // populate Clikt delegates

        val result = testClass.fetchCommits("branch1", emptyList())

        val expected =
            listOf(
                CommitInfo(hash = "408c5", subject = "[JEWEL-007] Casino Royale Easter Egg"),
                CommitInfo(hash = "be0b1", subject = "[JEWEL-101] Adding README"),
            )

        assertEquals(expected, result)
    }

    @Test
    fun `fetchCommits skips unparseable commit`() = runTest {
        val fakeRunner = FakeCommandRunner { command ->
            when {
                command.contains("--pretty=format:") -> {
                    CmdResult.Success(
                        """
                            408c5::COMMIT::[JEWEL-007] Casino Royale Easter Egg,2026-05-13 13:13:13 +0300
                            be0b1::COMMIT::[JEWEL-101] Adding README,invalid date
                            """
                            .trimIndent()
                    )
                }
                else -> CmdResult.Success("")
            }
        }
        val testClass = CompareBranchesCommand(fakeRunner, pathExists = { true })
        testClass.test(listOf("branch1", "branch2", "--since", "2026-05-10")) // populate Clikt delegates

        val result = testClass.fetchCommits("branch1", emptyList())

        val expected = listOf(CommitInfo(hash = "408c5", subject = "[JEWEL-007] Casino Royale Easter Egg"))

        assertEquals(expected, result)
    }

    @Test
    fun `fetchCommits filters out commits before sinceDate`() = runTest {
        val fakeRunner = FakeCommandRunner { command ->
            when {
                command.contains("--pretty=format:") -> {
                    CmdResult.Success(
                        """
                            408c5::COMMIT::[JEWEL-007] Casino Royale Easter Egg,2026-05-13 13:13:13 +0300
                            be0b1::COMMIT::[JEWEL-101] Adding README,2026-05-15 22:27:38 +0200
                            """
                            .trimIndent()
                    )
                }
                else -> CmdResult.Success("")
            }
        }
        val testClass = CompareBranchesCommand(fakeRunner, pathExists = { true })
        testClass.test(listOf("branch1", "branch2", "--since", "2026-05-14"))

        val result = testClass.fetchCommits("branch1", emptyList())

        val expected = listOf(CommitInfo(hash = "be0b1", subject = "[JEWEL-101] Adding README"))

        assertEquals(expected, result)
    }

    @Test
    fun `fetchCommits with empty git output should return an empty list`() = runTest {
        val fakeRunner = FakeCommandRunner { command ->
            when {
                command.contains("--pretty=format:") -> {
                    CmdResult.Success(
                        """
                            408c5::COMMIT::[JEWEL-007] Casino Royale Easter Egg,2026-05-13 13:13:13 +0300
                            be0b1::COMMIT::[JEWEL-101] Adding README,invalid date
                            """
                            .trimIndent()
                    )
                }
                else -> CmdResult.Success("")
            }
        }
        val testClass = CompareBranchesCommand(fakeRunner, pathExists = { true })
        testClass.test(listOf("branch1", "branch2", "--since", "2026-05-30"))

        val result = testClass.fetchCommits("branch1", emptyList())

        assertEquals(emptyList(), result)
    }

    @Test
    fun `fetchCommits with all commits filtered out should return an empty list`() = runTest {
        val fakeGitHubRunner = FakeCommandRunner { command ->
            when {
                command.contains("--pretty=format:") -> {
                    CmdResult.Success("")
                }
                else -> CmdResult.Success("")
            }
        }
        val testClass = CompareBranchesCommand(fakeGitHubRunner, pathExists = { true })
        testClass.test(listOf("branch1", "branch2"))

        val result = testClass.fetchCommits("branch1", emptyList())

        assertEquals(emptyList(), result)
    }

    @Test
    fun `run throws usage error when --no-build-changes and --build-only are both set`() = runTest {
        val result =
            testClass.test(
                listOf("branch1", "branch2", "--no-build-changes", "--build-only", "--since", "2026-05-10")
            )

        assertContains(result.output, "Error: --no-build-changes and --build-only are mutually exclusive.")
    }
}
