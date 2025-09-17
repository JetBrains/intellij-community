#!/usr/bin/env kotlin
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Import("utils.main.kts")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:5.0.3")
@file:Suppress("RAW_RUN_BLOCKING")

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

private class AnnotateApiDumpChangesCommand : SuspendingCliktCommand(name = "annotate") {
    private val verbose: Boolean by option("--verbose", "-v", help = "Enable verbose output.").flag(default = false)

    override fun help(context: Context): String = "Annotates API dump files for breaking changes against a base commit."

    override suspend fun run() {
        print("⏳ Locating Jewel root...")
        val jewelRoot = findJewelRoot() ?: exitWithError("Could not find the Jewel root directory.")
        println(" DONE: ${jewelRoot.canonicalPath}")

        val baseCommit = determineBaseCommit(jewelRoot)
        println("Checking against base commit: $baseCommit")

        println("\nValidating stable API dumps...")
        val stableViolations = validateDumps(experimental = false, baseCommit, jewelRoot) { it.name == "api-dump.txt" }

        println("\nValidating experimental API dumps...")
        val experimentalViolations =
            validateDumps(experimental = true, baseCommit, jewelRoot) { it.name == "api-dump-experimental.txt" }

        println("\nWriting summary...")
        writeSummary(stableViolations, experimentalViolations)

        println("\nDone processing API dumps")

        if (stableViolations) {
            exitWithError("Stable API breakages found.")
        } else {
            printlnSuccess("✅ No stable API breakages found.")
        }
    }

    private suspend fun determineBaseCommit(jewelRoot: File): String =
        if (checkPrNumber()) {
            requireGhTool()
            runCommand("gh pr view ${getPrNumber()} --json baseRefOid -q .baseRefOid", jewelRoot).getOrThrow().trim()
        } else {
            printlnWarn("GitHub PR number not found, falling back to checking against HEAD~1 instead")
            runCommand("git rev-parse HEAD~1", jewelRoot).getOrThrow().trim()
        }

    private data class ValidationResult(val file: File, val log: CharSequence, val foundBreakages: Boolean)

    private suspend fun validateDumps(
        experimental: Boolean,
        baseCommit: String,
        jewelRoot: File,
        dumpsFilter: (File) -> Boolean,
    ): Boolean {
        val samplesDir: String = File(jewelRoot, "samples").absolutePath
        val apiDumpFiles =
            jewelRoot.walkTopDown().filter { dumpsFilter(it) && !it.absolutePath.startsWith(samplesDir) }.toList()

        println()
        println("Detected API dumps:\n${apiDumpFiles.joinToString("\n") { " * ${it.toRelativeString(jewelRoot)}" }}")

        val results = coroutineScope {
            apiDumpFiles
                .map { file ->
                    async {
                        var foundBreakages = false
                        val log = buildString {
                            appendLine("\n  Checking ${file.toRelativeString(jewelRoot)}")

                            val isModifiedResult =
                                runCommand(
                                    "git diff --quiet $baseCommit -- ${file.absolutePath}",
                                    jewelRoot,
                                    exitOnError = false,
                                )

                            if (verbose) {
                                appendLine("    First diff result:\n${isModifiedResult.output}")
                            }

                            if (isModifiedResult.isFailure) {
                                appendLine("    Detected changes, investigating...")

                                val command = "git --no-pager diff --unified=0 $baseCommit -- ${file.absolutePath}"
                                if (verbose) {
                                    appendLine("      Running: $command...")
                                }

                                val diffResult = runCommand(command, jewelRoot)
                                if (verbose) {
                                    appendLine("      Second diff result:\n${diffResult.output}")
                                }

                                foundBreakages = processDiff(diffResult.output, file, experimental, this, jewelRoot)
                            }
                        }
                        ValidationResult(file, log, foundBreakages)
                    }
                }
                .awaitAll()
        }

        results.forEach { result -> print(result.log) }
        return results.any { it.foundBreakages }
    }

    private val chunkHeaderRegex = "^@@ \\-([0-9]+)(?:,[0-9]+)? \\+([0-9]+)".toRegex()

    private fun processDiff(
        diff: String,
        file: File,
        experimental: Boolean,
        log: StringBuilder,
        jewelRoot: File,
    ): Boolean {
        var foundBreakages = false
        var oldLineNum = 0
        var newLineNum = 0
        var lastLineWasRemoval = false

        diff.lines().forEach { line ->
            when {
                line.startsWith("@@") -> {
                    val match = chunkHeaderRegex.find(line)
                    if (match != null) {
                        oldLineNum = match.groupValues[1].toInt()
                        newLineNum = match.groupValues[2].toInt()
                    }
                    lastLineWasRemoval = false
                }
                line.startsWith("-") && !line.startsWith("---") -> {
                    reportBreakage(
                        file,
                        line,
                        log,
                        oldLineNum,
                        newLineNum,
                        experimental,
                        annotate = !lastLineWasRemoval,
                        jewelRoot,
                    )
                    foundBreakages = true
                    oldLineNum++
                    lastLineWasRemoval = true
                }
                line.startsWith("+") && !line.startsWith("+++") -> {
                    newLineNum++
                    lastLineWasRemoval = false
                }
                line.startsWith(" ") -> {
                    oldLineNum++
                    newLineNum++
                    lastLineWasRemoval = false
                }
            }
        }

        return foundBreakages
    }

    private fun reportBreakage(
        file: File,
        line: String,
        log: StringBuilder,
        oldLineNum: Int,
        newLineNum: Int,
        experimental: Boolean,
        annotate: Boolean,
        jewelRoot: File,
    ) {
        val lineContent =
            line
                .substring(1) // Skip the first character (either + or -)
                .replace("%", "%25") // Escape the % character
                .replace("\r", "%0D") // Escape the \r character
                .replace("\n", "%0A") // Escape the \n character

        val type = if (experimental) "experimental" else "stable"
        val message = "⚠️ Breaking $type API change:\n       line $oldLineNum removed: $lineContent"
        log.appendLine("    " + if (experimental) message.asWarning() else message.asError())

        if (!annotate) return

        // Use the magic log format that GitHub workflows accept to annotate code
        // See https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands
        val severity = if (experimental) "warning" else "error"
        log.append("::$severity ")

        val repoRoot = jewelRoot.parentFile.parentFile
        log.append("file=${file.toRelativeString(repoRoot)},")

        log.append("line=$newLineNum,")

        val title = if (experimental) "Breaking experimental API change" else "Breaking API change"
        log.appendLine("title=$title::This looks like a breaking API change, make sure it's intended.")
    }

    private fun writeSummary(stableViolations: Boolean, experimentalViolations: Boolean) {
        val summary = buildString {
            appendLine("## Binary check result")
            if (!stableViolations && !experimentalViolations) {
                appendLine("✅ No API breakages found.")
            } else {
                if (stableViolations) {
                    appendLine("❌ Stable API breakages found.")
                }
                if (experimentalViolations) {
                    appendLine("⚠️ Experimental API breakages found.")
                }
            }
        }

        println(summary.prependIndent())

        // Write the summary to the magical GITHUB_STEP_SUMMARY file
        // See https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands
        val summaryFile = System.getenv("GITHUB_STEP_SUMMARY")?.takeIf { !it.isBlank() }?.let { File(it) }
        if (summaryFile != null) {
            summaryFile.writeText(summary)
            println("Summary written to ${summaryFile.absolutePath}")
        } else {
            printlnWarn("GITHUB_STEP_SUMMARY environment variable not set")
        }
    }
}

runBlocking { AnnotateApiDumpChangesCommand().main(args) }
