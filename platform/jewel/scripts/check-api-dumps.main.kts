#!/usr/bin/env kotlin
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Import("utils.main.kts")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:5.0.3")
@file:Suppress("RAW_RUN_BLOCKING", "SSBasedInspection")

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.pgreze.process.Redirect
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import java.io.File

private class CheckUpdatedCommand : SuspendingCliktCommand(name = "check") {
    private val verbose: Boolean by option("--verbose", "-v", help = "Enable verbose output.").flag(default = false)
    
    override fun help(context: Context): String = "Runs APICheckTest and fails if test failures are detected."

    override suspend fun run() {
        print("⏳ Locating repository root...")
        val repoRoot = findCommunityRoot() ?: exitWithError("Could not find the repository root directory.")
        println(" DONE: ${repoRoot.canonicalPath}")

        val command = "./tests.cmd --module $TEST_MODULE --test $TEST_CLASS"
        if (verbose) println("Running: $command")

        // Stream output in real-time and save to a temporary file for later analysis
        val outputFile = File.createTempFile("api-check-output", ".log")
        val scriptFile = File.createTempFile("api-check-script", ".sh")
        try {
            println("Output will be streamed in real-time and saved to: ${outputFile.absolutePath}")

            // Create a temporary shell script to handle the pipe properly. The pipeline's exit status must be
            // tests.cmd's and not tee's, hence the pipefail; errexit makes a failing cd fatal, too.
            scriptFile.writeText(
                """
                #!/bin/bash
                set -e -o pipefail
                cd ${repoRoot.absolutePath.asShellLiteral()}
                $command | tee ${outputFile.absolutePath.asShellLiteral()}
                """
                    .trimIndent()
            )
            scriptFile.setExecutable(true)

            val result =
                runCommand(
                    command = scriptFile.absolutePath,
                    workingDir = repoRoot,
                    exitOnError = false,
                    timeoutAmount = 60.minutes,
                    outputRedirect = Redirect.PRINT,
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

    /**
     * Renders this string as a single-quoted shell literal. Paths embedded in generated shell source must be quoted
     * this way: inside double quotes the shell would still expand `$`, backticks and backslashes.
     */
    private fun String.asShellLiteral(): String = "'" + replace("'", """'\''""") + "'"

    private fun extractFailingModules(output: String): List<String> {
        val failurePattern = """##teamcity\[testFailed name='${Regex.escape(TEST_CLASS)}\.([^']+)'""".toRegex()
        return failurePattern.findAll(output).map { it.groupValues[1] }.distinct().sorted().toList()
    }

    private companion object {
        // tests.cmd needs both the JPS module that holds the test class, and the test pattern itself.
        const val TEST_MODULE = "intellij.platform.testFramework.monorepo.tests"
        const val TEST_CLASS = "com.intellij.platform.testFramework.monorepo.api.ApiCheckTest"
    }
}

runBlocking { CheckUpdatedCommand().main(args) }
