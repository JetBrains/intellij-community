package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assume
import org.junit.Test

class JewelBazelScriptsUtilsTest {
    @Test
    fun `DefaultCommandRunner aborts a command that exceeds timeoutAmount instead of blocking until it exits`() =
        runTest {
            // JUnit4 equivalent of @DisabledOnOs from JUnit5
            Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))

            var elapsed = 0L

            assertFailsWith<IllegalStateException> {
                elapsed = measureTimeMillis {
                    DefaultCommandRunner("sleep 10", workingDir = null, timeoutAmount = 1.seconds)
                }
            }

            // The underlying process sleeps for 10s; if the timeout weren't enforced, this call would
            // block for the full 10s (or longer). Give it generous headroom over the 1s bound to absorb
            // process-startup/scheduling overhead, while still failing if the old unbounded waitFor() regresses.
            assertTrue(elapsed < 5_000, "expected the command to be aborted around the 1s timeout, took ${elapsed}ms")
        }

    @Test
    fun `branchExists returns false instead of throwing when the branch does not exist`() = runTest {
        // git rev-parse --verify exits non-zero for exactly this case — the one branchExists exists to detect.
        val fakeRunner = FakeCommandRunner { CmdResult.Failure("fatal: Needed a single revision") }

        val result = branchExists("nonexistent-branch", File("."), fakeRunner)

        assertFalse(result)
    }

    @Test
    fun `branchExists returns true when git rev-parse succeeds`() = runTest {
        val fakeRunner = FakeCommandRunner { CmdResult.Success("abc123") }

        val result = branchExists("main", File("."), fakeRunner)

        assertTrue(result)
    }

    @Test
    fun `isDirectoryGitRepo returns false instead of throwing when the directory is not a git repo`() = runTest {
        val fakeRunner = FakeCommandRunner { CmdResult.Failure("fatal: not a git repository") }

        val result = isDirectoryGitRepo(File("."), fakeRunner)

        assertFalse(result)
    }

    @Test
    fun `getCurrentBranchName propagates a failing git command`() = runTest {
        val fakeRunner = FakeCommandRunner { CmdResult.Failure("fatal: not a git repository") }

        assertFailsWith<IllegalStateException> { getCurrentBranchName(File("."), fakeRunner) }
    }

    @Test
    fun `tokenizeCommand splits a plain space-separated command like the old naive split`() {
        val tokens = tokenizeCommand("git status --porcelain")

        assertEquals(listOf("git", "status", "--porcelain"), tokens)
    }

    @Test
    fun `tokenizeCommand collapses repeated spaces and tabs and ignores leading and trailing whitespace`() {
        val tokens = tokenizeCommand("  git\t\tstatus   --porcelain  ")

        assertEquals(listOf("git", "status", "--porcelain"), tokens)
    }

    @Test
    fun `tokenizeCommand returns an empty list for a blank command`() {
        val tokens = tokenizeCommand("   ")

        assertEquals(emptyList(), tokens)
    }

    @Test
    fun `tokenizeCommand keeps a single-quoted section as one token including its spaces`() {
        val tokens = tokenizeCommand("echo 'hello world'")

        assertEquals(listOf("echo", "hello world"), tokens)
    }

    @Test
    fun `tokenizeCommand does not process backslash escapes inside single quotes`() {
        val tokens = tokenizeCommand("echo 'a\\b'")

        assertEquals(listOf("echo", "a\\b"), tokens)
    }

    @Test
    fun `tokenizeCommand keeps a double-quoted section as one token including its spaces`() {
        val tokens = tokenizeCommand("echo \"hello world\"")

        assertEquals(listOf("echo", "hello world"), tokens)
    }

    @Test
    fun `tokenizeCommand resolves backslash escapes for quote, backslash, dollar sign, and backtick inside double quotes`() {
        val tokens = tokenizeCommand("echo \"a\\\"b\\\\c\\\$d\\`e\"")

        assertEquals(listOf("echo", "a\"b\\c\$d`e"), tokens)
    }

    @Test
    fun `tokenizeCommand leaves a backslash untouched when it is not followed by an escapable character`() {
        val tokens = tokenizeCommand("echo \"a\\nb\"")

        assertEquals(listOf("echo", "a\\nb"), tokens)
    }

    @Test
    fun `tokenizeCommand joins a quoted section in the middle of a word into the same token`() {
        val tokens = tokenizeCommand("--flag=\"some value\"")

        assertEquals(listOf("--flag=some value"), tokens)
    }

    @Test
    fun `tokenizeCommand concatenates adjacent quoted and unquoted segments into a single token`() {
        val tokens = tokenizeCommand("a'b c'd")

        assertEquals(listOf("ab cd"), tokens)
    }

    @Test
    fun `tokenizeCommand handles a git pretty-format single-quoted argument`() {
        val tokens = tokenizeCommand("git log --pretty=format:'%H %s'")

        assertEquals(listOf("git", "log", "--pretty=format:%H %s"), tokens)
    }

    @Test
    fun `tokenizeCommand throws on an unterminated single quote`() {
        assertFailsWith<IllegalStateException> { tokenizeCommand("echo 'unterminated") }
    }

    @Test
    fun `tokenizeCommand throws on an unterminated double quote`() {
        assertFailsWith<IllegalStateException> { tokenizeCommand("echo \"unterminated") }
    }
}
