package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class CheckApiDumpsTest {
    private val fakeRunner = FakeCommandRunner { CmdResult.Success("") }
    private val testClass = CheckUpdatedCommand(fakeRunner)

    @Test
    fun `buildApiCheckCommand passes both the module and the test pattern`() {
        val result = buildApiCheckCommand()

        assertEquals(
            "./tests.cmd --module intellij.platform.testFramework.monorepo.tests " +
                "--test com.intellij.platform.testFramework.monorepo.api.ApiCheckTest",
            result,
        )
    }

    @Test
    fun `buildApiCheckScript enables errexit and pipefail so a real failure survives the tee pipe`() {
        val result = buildApiCheckScript(File("/repo"), File("/tmp/out.log"), "./tests.cmd")

        assertTrue(result.lines().contains("set -e -o pipefail"))
    }

    @Test
    fun `buildApiCheckScript quotes the repo root and output paths as single-quoted shell literals`() {
        // A path containing a $, a space and a literal single quote — exactly the case the shell-quoting
        // fix targets: unquoted or double-quoted, the shell would still expand '$(evil)' as a command.
        val dangerousRoot = File("/repo/weird '$(evil)' dir")

        val result = buildApiCheckScript(dangerousRoot, File("/tmp/out.log"), "./tests.cmd")

        assertTrue(result.contains("cd '/repo/weird '\\''\$(evil)'\\'' dir'"))
    }

    @Test
    fun `buildApiCheckScript pipes the command through tee to both the console and the output file`() {
        val result = buildApiCheckScript(File("/repo"), File("/tmp/out.log"), "./tests.cmd --module m --test t")

        assertTrue(result.contains("./tests.cmd --module m --test t | tee '/tmp/out.log'"))
    }

    @Test
    fun `extractFailingModules returns module name from single failure`() {
        val output = "##teamcity[testFailed name='com.intellij.platform.testFramework.monorepo.api.ApiCheckTest.Fail'"

        val result = testClass.extractFailingModules(output)

        assertEquals(listOf("Fail"), result)
    }

    @Test
    fun `extractFailingModules returns modules sorted alphabetically`() {
        val output =
            """"
            ##teamcity[testFailed name='com.intellij.platform.testFramework.monorepo.api.ApiCheckTest.Z'
            ##teamcity[testFailed name='com.intellij.platform.testFramework.monorepo.api.ApiCheckTest.A'
            ##teamcity[testFailed name='com.intellij.platform.testFramework.monorepo.api.ApiCheckTest.G'
            """
                .trimIndent()

        val result = testClass.extractFailingModules(output)

        assertEquals(listOf("A", "G", "Z"), result)
    }

    @Test
    fun `extractFailingModules deduplicates repeated failures`() {
        val output =
            """
            ##teamcity[testFailed name='com.intellij.platform.testFramework.monorepo.api.ApiCheckTest.Fail'
            ##teamcity[testFailed name='com.intellij.platform.testFramework.monorepo.api.ApiCheckTest.Fail'
            """
                .trimIndent()

        val result = testClass.extractFailingModules(output)

        assertEquals(listOf("Fail"), result)
    }

    @Test
    fun `extractFailingModules returns empty list when no failures found`() {
        val output =
            """
            ##teamcity[testSuccess name='com.intellij.platform.testFramework.monorepo.api.ApiCheckTest.Cool'
            ##teamcity[testSuccess name='com.intellij.platform.testFramework.monorepo.api.ApiCheckTest.Yeah'
            """
                .trimIndent()

        val result = testClass.extractFailingModules(output)

        assertEquals(emptyList(), result)
    }

    @Test
    fun `extractFailingModules empty output should return an empty list`() {
        val result = testClass.extractFailingModules("")

        assertEquals(emptyList(), result)
    }
}
