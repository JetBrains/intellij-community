@file:Suppress("RAW_RUN_BLOCKING", "SSBasedInspection")
package org.jetbrains.jewel.scripts.bazel

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

// tests.cmd needs both the JPS module that holds the test class, and the test pattern itself.
private const val TEST_MODULE = "intellij.platform.testFramework.monorepo.tests"
private const val TEST_CLASS = "com.intellij.platform.testFramework.monorepo.api.ApiCheckTest"

fun main(args: Array<String>) {
    runBlocking { CheckUpdatedCommand().main(args) }
}

/**
 * Renders this string as a single-quoted shell literal. Paths embedded in generated shell source must be quoted this
 * way: inside double quotes the shell would still expand `$`, backticks and backslashes.
 */
internal fun String.asShellLiteral(): String = "'" + replace("'", """'\''""") + "'"

internal fun buildApiCheckCommand(): String = "./tests.cmd --module $TEST_MODULE --test $TEST_CLASS"

/**
 * Builds the shell script that runs [command] and tees its output to [outputFile], from [repoRoot].
 *
 * The pipeline's exit status must be [command]'s and not tee's, hence `pipefail`; `errexit` makes a failing `cd`
 * fatal, too.
 */
internal fun buildApiCheckScript(repoRoot: File, outputFile: File, command: String): String =
    """
    #!/bin/bash
    set -e -o pipefail
    cd ${repoRoot.absolutePath.asShellLiteral()}
    $command | tee ${outputFile.absolutePath.asShellLiteral()}
    """
        .trimIndent()

internal class CheckUpdatedCommand(private val commandRunner: CommandRunner = DefaultCommandRunner) :
    SuspendingCliktCommand("check") {
    private val verbose: Boolean by option("--verbose", "-v", help = "Enable verbose output.").flag(default = false)

    override fun help(context: Context): String = "Runs APICheckTest and fails if test failures are detected."

    override suspend fun run() =
        withContext(Dispatchers.IO) {
            print("⏳ Locating repository root...")
            val repoRoot = getBuildWorkspaceDirectory()
            println(" DONE: ${repoRoot.canonicalPath}")

            val command = buildApiCheckCommand()
            if (verbose) println("Running: $command")

            val outputFile = File.createTempFile("api-check-output", ".log")
            val scriptFile = File.createTempFile("api-check-script", ".sh")

            try {
                println("Output will be streamed in real-time and saved to: ${outputFile.absolutePath}")

                scriptFile.writeText(buildApiCheckScript(repoRoot, outputFile, command))
                scriptFile.setExecutable(true)

                val result =
                    commandRunner(
                        command = scriptFile.absolutePath,
                        workingDir = repoRoot,
                        exitOnError = false,
                        timeoutAmount = 60.minutes,
                        streamOutput = true,
                    )

                // Read the captured output for analysis
                val output = outputFile.readText()

                // Extract failing module names from TeamCity test failure messages
                val failingModules = extractFailingModules(output)

                if (failingModules.isNotEmpty()) {
                    val moduleList = failingModules.joinToString(", ")
                    exitWithError("❌ API check test failed — failing modules: $moduleList")
                } else if (result.isFailure) {
                    // The output scraping only recognises TeamCity test failures; anything else that makes the
                    // command fail (bad invocation, build error, crash) must still be reported as a failure.
                    exitWithError("❌ API check test command failed. See the output above for details.")
                } else {
                    printlnSuccess("✅ API check test passed — no test failures detected.")
                }
            } finally {
                outputFile.delete()
                scriptFile.delete()
            }
        }

    internal fun extractFailingModules(output: String): List<String> {
        val failurePattern = """##teamcity\[testFailed name='${Regex.escape(TEST_CLASS)}\.([^']+)'""".toRegex()
        return failurePattern.findAll(output).map { it.groupValues[1] }.distinct().sorted().toList()
    }
}
