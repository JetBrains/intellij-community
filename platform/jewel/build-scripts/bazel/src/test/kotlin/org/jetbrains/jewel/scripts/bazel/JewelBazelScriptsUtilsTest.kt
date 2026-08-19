package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.system.measureTimeMillis
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
}
