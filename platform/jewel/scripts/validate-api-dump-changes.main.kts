#!/usr/bin/env kotlin
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Import("utils.main.kts")

// Coroutine dependency for KTS scripts — we don't actually need it but the script doesn't compile without
// _some_ depends on annotation for whatever reason
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
@file:Suppress("RAW_RUN_BLOCKING")

import kotlin.system.exitProcess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File

private val baseDir = File("").absoluteFile

require(baseDir.isDirectory && baseDir.name == "jewel") { "This script must be run from the platform/jewel folder" }

private data class ValidationResult(val file: File, val log: CharSequence, val foundBreakages: Boolean)

private val samplesDir: String = File(baseDir, "samples").absolutePath

private suspend fun validateDumps(experimental: Boolean, dumpsFilter: (File) -> Boolean): Boolean {
    val apiDumpFiles =
        baseDir
            .walkTopDown()
            // Avoid picking up dumps in the samples dir. This is a naive check that assumes no symlinks.
            // If we have symlinks we need to use canonicalPath and walk up the tree to have a reliable check.
            .filter { dumpsFilter(it) && !it.absolutePath.startsWith(samplesDir) }
            .toList()

    println()

    println("Detected API dumps:\n${apiDumpFiles.joinToString("\n") { " * ${it.toRelativeString(baseDir)}" }}")

    val results = coroutineScope {
        apiDumpFiles
            .map { file ->
                async {
                    var foundBreakages = false
                    val log = buildString {
                        appendLine("\n  Checking ${file.toRelativeString(baseDir)}")

                        val isModifiedResult =
                            runCommand(
                                "git diff --quiet $baseCommit -- ${file.absolutePath}",
                                baseDir,
                                exitOnError = false,
                            )

                        if ("--verbose" in args) {
                            appendLine("    First diff result:\n${isModifiedResult.output}")
                        }

                        if (isModifiedResult.isFailure) {
                            appendLine("    Detected changes, investigating...")

                            val command = "git --no-pager diff --unified=0 $baseCommit -- ${file.absolutePath}"
                            if ("--verbose" in args) {
                                appendLine("      Running: $command...")
                            }

                            val diffResult = runCommand(command, baseDir)
                            if ("--verbose" in args) {
                                appendLine("      Second diff result:\n${isModifiedResult.output}")
                            }

                            foundBreakages = processDiff(diffResult.output, file, experimental, this)
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

private fun processDiff(diff: String, file: File, experimental: Boolean, log: StringBuilder): Boolean {
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
                // We only annotate once per block of removals — but log all removals anyway
                reportBreakage(file, line, log, oldLineNum, newLineNum, experimental, annotate = !lastLineWasRemoval)
                foundBreakages = true
                oldLineNum++
                lastLineWasRemoval = true
            }

            line.startsWith("+") -> {
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
) {
    val lineContent =
        line
            .substring(1) // Skip the first character (either + or -)
            .replace("%", "%25") // Escape the % character
            .replace("\r", "%0D") // Escape the \r character
            .replace("\n", "%0A") // Escape the \n character

    val color = if (experimental) "\u001b[0;33m" else "\u001b[0;31m"
    val type = if (experimental) "experimental" else "stable"
    log.appendLine("    $color⚠️ Breaking $type API change:\n       line $oldLineNum removed: $lineContent\u001b[0m")

    if (!annotate) return

    // Emit an error or warning annotation on the GitHub action using workflow-commands
    val severity = if (experimental) "warning" else "error"
    log.append("::$severity ")
    log.append("file=${file.toRelativeString(baseDir.absoluteFile.parentFile.parentFile)},")

    // For a removal, GitHub annotations must be on a line that exists in the new file.
    // For a pure deletion, this is the line *before* the deletion. For a modification,
    // this is the first line of the new modified block. The `newLineNum` from the
    // hunk header gives us exactly this information.
    val annotationLine = newLineNum
    log.append("line=$annotationLine,")

    val title = if (experimental) "Breaking experimental API change" else "Breaking API change"
    log.appendLine("title=$title::This looks like a breaking API change, make sure it's intended.")
}

private val hasPrNumber = checkPrNumber()

private val baseCommit = runBlocking {
    if (hasPrNumber) {
        requireGhTool()
        runCommand("gh pr view ${getPrNumber()} --json baseRefOid -q .baseRefOid", baseDir).getOrThrow().trim()
    } else {
        echoWarn("GitHub PR number not found, falling back to checking against HEAD~1 instead")
        runCommand("git rev-parse HEAD~1", baseDir).getOrThrow().trim()
    }
}

println("Checking against base commit: $baseCommit")

println("\nValidating stable API dumps...")

private val stableViolations = runBlocking { validateDumps(experimental = false) { it.name == "api-dump.txt" } }

println("\nValidating experimental API dumps...")

private val experimentalViolations = runBlocking {
    validateDumps(experimental = true) { it.name == "api-dump-experimental.txt" }
}

println("\nWriting summary...")

runBlocking {
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

    val summaryFile = System.getenv("GITHUB_STEP_SUMMARY")?.takeIf { !it.isBlank() }?.let { File(it) }
    if (summaryFile != null) {
        summaryFile.writeText(summary)
        println("Summary written to ${summaryFile.absolutePath}")
    } else {
        echoWarn("GITHUB_STEP_SUMMARY environment variable not set")
    }
}

println("\nDone processing API dumps")

// Fail the check for stable API violations
if (stableViolations) exitProcess(1)
