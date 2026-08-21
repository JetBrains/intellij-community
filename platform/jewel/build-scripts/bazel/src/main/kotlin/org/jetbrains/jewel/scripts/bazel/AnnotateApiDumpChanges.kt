@file:Suppress("RAW_RUN_BLOCKING", "SSBasedInspection")

package org.jetbrains.jewel.scripts.bazel

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

private val chunkHeaderRegex = "^@@ \\-([0-9]+)(?:,[0-9]+)? \\+([0-9]+)".toRegex()

fun main(args: Array<String>) {
    runBlocking { AnnotateApiDumpChangesCommand().main(args) }
}

internal class AnnotateApiDumpChangesCommand(private val commandRunner: CommandRunner = DefaultCommandRunner) :
    SuspendingCliktCommand(name = "annotate") {
    private val verbose: Boolean by option("--verbose", "-v", help = "Enable verbose output.").flag(default = false)

    override fun help(context: Context): String = "Annotates API dump files for breaking changes against a base commit."

    override suspend fun run() {
        print("⏳ Locating Jewel root...")
        val jewelRoot = getJewelRoot() ?: exitWithError("Could not find the Jewel root directory.")
        println(" DONE: ${jewelRoot.canonicalPath}")

        val baseCommit = determineBaseCommit(jewelRoot)
        println("Checking against base commit: $baseCommit")

        println("\nValidating stable API dumps...")
        val stableViolations =
            validateDumps(verbose, experimental = false, baseCommit, jewelRoot, commandRunner) {
                it.name == "api-dump.txt"
            }

        println("\nValidating experimental API dumps...")
        val experimentalViolations =
            validateDumps(verbose, experimental = true, baseCommit, jewelRoot, commandRunner) {
                it.name == "api-dump-experimental.txt"
            }

        println("\nCreating summary...")
        val summary = buildSummary(stableViolations, experimentalViolations)
        println(summary.prependIndent())

        println("\nWriting summary to GitHub Job...")
        writeSummary(summary)

        println("\nDone processing API dumps")

        if (stableViolations) {
            exitWithError("Stable API breakages found.")
        } else {
            printlnSuccess("✅ No stable API breakages found.")
        }
    }

    private suspend fun determineBaseCommit(jewelRoot: File) =
        if (checkPrNumber()) {
            requireGhTool()
            commandRunner("gh pr view ${getPrNumber()} --json baseRefOid -q .baseRefOid", jewelRoot).getOrThrow()
        } else {
            printlnWarn("GitHub PR number not found, falling back to checking against HEAD~1 instead")
            commandRunner("git rev-parse HEAD~1", jewelRoot).getOrThrow()
        }

    private fun buildSummary(stableViolations: Boolean, experimentalViolations: Boolean) = buildString {
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

    private fun writeSummary(summary: String) {
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

    /**
     * Requires the GitHub CLI tool (`gh`) to be present on the system's PATH. Exits the process with an error code if
     * the tool is not found.
     */
    private suspend fun requireGhTool() {
        if (checkGhTool()) return

        exitWithError("ERROR: the GitHub CLI tool must be present on the PATH.")
    }

    /**
     * Checks if the GitHub CLI tool (`gh`) is present on the system's PATH.
     *
     * @return `true` if the `gh` tool is found, `false` otherwise.
     */
    private suspend fun checkGhTool() = commandRunner("which gh", null, exitOnError = false).isSuccess
}

/**
 * Checks if the PR number environment variable (`PR_NUMBER`) is set and is a valid integer.
 *
 * @return `true` if `PR_NUMBER` is set and is an integer, `false` otherwise.
 */
private fun checkPrNumber() = System.getenv("PR_NUMBER")?.trim()?.toIntOrNull() != null

/**
 * Gets the value of the PR number environment variable (`PR_NUMBER`).
 *
 * @return The PR number as a string.
 * @throws IllegalStateException if the `PR_NUMBER` environment variable is not set.
 */
private fun getPrNumber() = checkNotNull(System.getenv("PR_NUMBER")?.trim()) { "PR number not set" }

internal fun processDiff(diff: String, file: File, experimental: Boolean): List<BreakingChange> {
    val breakingChanges = mutableListOf<BreakingChange>()
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
                breakingChanges.add(
                    BreakingChange(
                        file,
                        line
                            .substring(1) // Skip the first character (either + or -)
                            .replace("%", "%25"), // Escape the % character
                        experimental,
                        oldLineNum,
                        newLineNum,
                        !lastLineWasRemoval,
                    )
                )
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

    return breakingChanges
}

internal suspend fun validateDumps(
    verbose: Boolean,
    experimental: Boolean,
    baseCommit: String,
    jewelRoot: File,
    commandRunner: CommandRunner,
    dumpsFilter: (File) -> Boolean,
): Boolean {
    val samplesDir = File(jewelRoot, "samples").absolutePath
    val apiDumpFiles =
        jewelRoot.walkTopDown().filter { dumpsFilter(it) && !it.absolutePath.startsWith(samplesDir) }.toList()

    println()
    println("Detected API dumps:\n${apiDumpFiles.joinToString("\n") { " * ${it.toRelativeString(jewelRoot)}" }}")

    val results = coroutineScope {
        apiDumpFiles
            .map { file ->
                async {
                    val breakingChanges = mutableListOf<BreakingChange>()
                    val log = buildString {
                        appendLine("\n  Checking ${file.toRelativeString(jewelRoot)}")

                        val isModifiedResult =
                            commandRunner(
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

                            val diffResult = commandRunner(command, jewelRoot)
                            if (verbose) {
                                appendLine("      Second diff result:\n${diffResult.output}")
                            }

                            breakingChanges.addAll(processDiff(diffResult.output, file, experimental))
                        }
                        breakingChanges.forEach { it.formatLog(this, jewelRoot) }
                    }
                    ValidationResult(file, log, breakingChanges)
                }
            }
            .awaitAll()
    }

    results.forEach { result -> print(result.log) }
    return results.any { it.breakingChanges.isNotEmpty() }
}

internal data class BreakingChange(
    val file: File,
    val lineContent: String,
    val experimental: Boolean,
    val oldLineNum: Int,
    val newLineNum: Int,
    val annotate: Boolean,
) {
    fun formatLog(log: StringBuilder, jewelRoot: File) {
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
}

internal data class ValidationResult(val file: File, val log: String, val breakingChanges: List<BreakingChange>)
