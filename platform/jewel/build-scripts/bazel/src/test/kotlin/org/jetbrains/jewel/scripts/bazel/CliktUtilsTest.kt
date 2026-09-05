package org.jetbrains.jewel.scripts.bazel

import com.github.ajalt.clikt.core.PrintMessage
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CliktUtilsTest {
    @Test
    fun `checkGhTool returns true when which gh succeeds`() = runTest {
        val fakeRunner = FakeCommandRunner { CmdResult.Success("/usr/local/bin/gh") }

        assertTrue(checkGhTool(fakeRunner))
    }

    @Test
    fun `checkGhTool returns false when which gh fails`() = runTest {
        val fakeRunner = FakeCommandRunner { CmdResult.Failure("gh not found") }

        assertFalse(checkGhTool(fakeRunner))
    }

    @Test
    fun `requireGhTool returns normally when gh is present`() = runTest {
        val fakeRunner = FakeCommandRunner { CmdResult.Success("/usr/local/bin/gh") }

        requireGhTool(fakeRunner) // Does not throw.
    }

    @Test
    fun `requireGhTool exits with an error when gh is absent`() = runTest {
        val fakeRunner = FakeCommandRunner { CmdResult.Failure("gh not found") }

        assertFailsWith<PrintMessage> { requireGhTool(fakeRunner) }
    }
}
