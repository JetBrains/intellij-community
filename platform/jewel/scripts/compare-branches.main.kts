#!/usr/bin/env kotlin
@file:Suppress("RAW_RUN_BLOCKING")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:5.0.3")
@file:Import("utils.main.kts")

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

/** Configuration for the branch comparison script. Defines paths to check and git log formatting options. */
private object Config {
    /** Paths to monitor for changes - platform components and build files */
    val PATHS_TO_CHECK =
        listOf(
            "platform/jewel",
            "libraries/skiko",
            "libraries/compose-runtime-desktop",
            "libraries/compose-foundation-desktop",
            "libraries/compose-foundation-desktop-junit",
            "libraries/detekt-compose-rules",
            "build/src/org/jetbrains/intellij/build/JewelMavenArtifacts.kt",
            "build/src/JewelMavenArtifactsBuildTarget.kt",
        )
    const val DELIMITER = "::COMMIT::"
    const val GIT_LOG_FORMAT = "%H${DELIMITER}%s"
}

/** Represents a git commit with its hash and subject line. */
private data class CommitInfo(val hash: String, val subject: String)

/** Result of parsing a git log line */
private sealed class ParseResult {
    data class Success(val commit: CommitInfo) : ParseResult()

    object DateFiltered : ParseResult()

    object ParseError : ParseResult()
}

private class CompareBranchesCommand : SuspendingCliktCommand() {
    private val verbose: Boolean by
        option("--verbose", help = "Prints the normalized commit subjects being compared.").flag(default = false)
    private val jewelOnly: Boolean by
        option("--jewel-only", help = "Only prints commits whose subject contains the word 'jewel' (case insensitive).")
            .flag(default = false)
    private val showHashes: Boolean by
        option("--hash", help = "Show commit hashes in the output.").flag(default = false)
    private val noBuildChanges: Boolean by
        option(
                "--no-build-changes",
                help = "Exclude commits that only affect the build/ directory. Mutually exclusive with --build-only.",
            )
            .flag(default = false)
    private val buildOnly: Boolean by
        option(
                "--build-only",
                help =
                    "Only check commits that affect the build/ directory. Mutually exclusive with --no-build-changes.",
            )
            .flag(default = false)
    private val branch1: String by argument(help = "The first branch to compare.")
    private val branch2: String by argument(help = "The second branch to compare.")
    private val sinceDate: String by
        option(
                "--since",
                help =
                    "The start date for the commit range (yyyy-mm-dd). " +
                        "If omitted, it will be inferred from the latest release in RELEASE NOTES.md.",
            )
            .defaultLazy {
                val since = getLatestReleaseDate(findJewelRoot() ?: File(".").canonicalFile)
                if (since.isNullOrBlank()) {
                    printlnErr(
                        "Error: --since is required if RELEASE NOTES.md does not exist or contain a release date."
                    )
                    exitProcess(1)
                }
                since
            }

    override fun help(context: Context): String =
        "Compares commits affecting specific paths between two branches by commit message."

    override suspend fun run() {
        // Validate mutually exclusive flags
        if (noBuildChanges && buildOnly) {
            printlnErr("Error: --no-build-changes and --build-only are mutually exclusive.")
            exitProcess(1)
        }

        val pathsToCheck = validateEnvironment()
        val commits1 = fetchCommits(branch1, pathsToCheck)
        val commits2 = fetchCommits(branch2, pathsToCheck)

        if (verbose) {
            println(
                "📊 Found ${commits1.size} commits in '${branch1}' and ${commits2.size} commits in '${branch2}' since ${sinceDate}"
            )

            if (commits1.isNotEmpty()) {
                println("\n--- Commits for '${branch1}' ---")
                commits1.forEach(::printCommitInfoLine)
                println()
            }

            if (commits2.isNotEmpty()) {
                println("\n--- Commits for '${branch2}' ---")
                commits2.forEach(::printCommitInfoLine)
                println()
            }
        }

        compareAndDisplayResults(commits1, commits2)
    }

    /** Validates the environment and prerequisites, returning the paths to check. */
    private suspend fun validateEnvironment(): List<String> {
        print("⏳ Locating IntelliJ Community root...")

        val communityRoot = findCommunityRoot()
        if (communityRoot == null || !communityRoot.isDirectory) {
            println()
            printlnErr(
                "    Could not find the IntelliJ Community root directory. " +
                    "Please make sure you're running the script from somewhere inside it."
            )
            exitProcess(1)
        }

        println(" DONE: ${communityRoot.absolutePath}")

        // Validate git repository
        if (!isDirectoryGitRepo(communityRoot)) {
            printlnErr("Error: Not a git repository.")
            exitProcess(1)
        }

        // Validate branches exist
        if (!branchExists(branch1, communityRoot)) {
            printlnErr("Error: Branch '$branch1' not found.")
            exitProcess(1)
        }

        if (!branchExists(branch2, communityRoot)) {
            printlnErr("Error: Branch '$branch2' not found.")
            exitProcess(1)
        }

        // Determine which paths to check based on flags
        val pathsToCheck =
            when {
                noBuildChanges -> Config.PATHS_TO_CHECK.filterNot { it.startsWith("build") }
                buildOnly -> Config.PATHS_TO_CHECK.filter { it.startsWith("build") }
                else -> Config.PATHS_TO_CHECK
            }

        // Validate paths exist
        var anyPathMissing = false
        for (path in pathsToCheck) {
            val file = File(communityRoot, path)
            if (!file.exists()) {
                printlnErr("Error: Path to check does not exist: $path")
                anyPathMissing = true
            }
        }
        if (anyPathMissing) exitProcess(1)

        // Display comparison parameters
        println("🔍 Comparing commits between '$branch1' and '$branch2' since $sinceDate for paths:")
        pathsToCheck.forEach { println("  - $it") }
        if (jewelOnly) println("[Only including commits explicitly tagged as Jewel]")
        if (noBuildChanges) println("[Excluding build/ directory changes]")
        if (buildOnly) println("[Only including build/ directory changes]")
        println()
        return pathsToCheck
    }

    /**
     * Fetches commits for a given branch, filtering by author date.
     *
     * Uses the author date instead of the commit date to properly handle cherry-picks.
     */
    private suspend fun fetchCommits(branch: String, pathsToCheck: List<String>): List<CommitInfo> {
        val communityRoot = findCommunityRoot()!!
        val pathsArg = pathsToCheck.joinToString(separator = " ") { "-- $it" }

        println("📋 Fetching commits from '$branch'...")

        val result =
            runCommand("git log $branch --pretty=format:'${Config.GIT_LOG_FORMAT},%ai' $pathsArg", communityRoot)
        val allLines = result.output.lines()
        val nonBlankLines = allLines.filter { it.isNotBlank() }

        if (verbose) {
            println("  📊 Raw output: ${allLines.size} lines total, ${nonBlankLines.size} non-blank")
        }

        // Parse commit messages and filter by author date
        val commits = mutableListOf<CommitInfo>()
        val unparseable = mutableListOf<String>()
        var dateFilteredCount = 0

        nonBlankLines.forEach { line ->
            when (val result = parseCommitWithDateResult(line)) {
                is ParseResult.Success -> commits.add(result.commit)
                is ParseResult.DateFiltered -> dateFilteredCount++
                is ParseResult.ParseError -> unparseable.add(line)
            }
        }

        if (verbose) {
            println("  ✅ Parsed ${commits.size} commits, filtered out $dateFilteredCount commits by date")
        }

        // Report truly unparseable commits only
        if (unparseable.isNotEmpty()) {
            printlnWarn(
                "  ⚠️  Warning: ${unparseable.size} commits from '$branch' could not be parsed due to format issues:"
            )
            for (line in unparseable.take(5)) { // Only show the first 5 to avoid spam
                println("    • ${line.take(100)}${if (line.length > 100) "..." else ""}")
            }
            if (unparseable.size > 5) {
                println("    ... and ${unparseable.size - 5} more")
            }
        }

        return commits
    }

    /** Parses a git log line and returns a ParseResult indicating success, date filtering, or parse error */
    private fun parseCommitWithDateResult(line: String): ParseResult {
        // Remove surrounding quotes first
        val cleanLine = line.trim().removeSurrounding("'")

        // Author dates have a consistent format: YYYY-MM-DD HH:MM:SS +ZZZZ
        // Use regex to find the author date at the end of the line
        val authorDateRegex = Regex("""^(.+),(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} [+\-]\d{4})$""")
        val matchResult = authorDateRegex.find(cleanLine) ?: return ParseResult.ParseError

        val commitPart = matchResult.groupValues[1]
        val authorDatePart = matchResult.groupValues[2]

        val commit = parseCommitLine(commitPart)

        // Filter by author date (handles cherry-picks correctly) using proper date parsing
        return if (isCommitAfterDate(authorDatePart, sinceDate)) {
            ParseResult.Success(commit)
        } else {
            ParseResult.DateFiltered
        }
    }

    /**
     * Compares author date with since date using proper datetime parsing. Handles timezone-aware comparison properly.
     */
    private fun isCommitAfterDate(authorDateStr: String, sinceDateStr: String): Boolean {
        return try {
            // Parse the git author date (format: "2025-09-25 12:37:32 +0200")
            val authorDate = ZonedDateTime.parse(authorDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"))

            // Parse the "since" date (format: "2025-08-25" -> start of day in local timezone)
            val sinceDate =
                LocalDate.parse(sinceDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    .atStartOfDay()
                    .atZone(authorDate.zone) // Use same timezone as author for fair comparison

            authorDate.isAfter(sinceDate) || authorDate.isEqual(sinceDate)
        } catch (e: Exception) {
            printlnWarn(
                "    Warning: Failed to parse dates - author: '$authorDateStr', since: '$sinceDateStr' (${e.message})"
            )
            // Fallback to string comparison if parsing fails
            authorDateStr >= sinceDateStr
        }
    }

    /**
     * Compares commits between branches and displays results. Cherry-picked commits with identical subjects are
     * considered the same.
     */
    private fun compareAndDisplayResults(commits1: List<CommitInfo>, commits2: List<CommitInfo>) {
        val horizontalLine = "------------------------------------------------------------------"
        println(horizontalLine)

        // Find commits unique to branch1 (by comparing normalized subjects)
        val branch2Subjects = commits2.map { it.subject }.toSet()
        val commitsInBranch1Only = commits1.filter { (_, subject) -> subject !in branch2Subjects }

        if (verbose) {
            println("Analyzing commits in '$branch1' but not '$branch2':")
            println("  Raw list: ${commitsInBranch1Only.joinToString { it.subject }}")
            println()
        }

        if (commitsInBranch1Only.isNotEmpty()) {
            println("Commits in '$branch1' but not '$branch2':")
            commitsInBranch1Only.forEach(::printCommitInfoLine)
        } else {
            println("✅ No unique commits found in '$branch1'.")
        }

        println()
        println(horizontalLine)

        // Find commits unique to branch2
        val branch1Subjects = commits1.map { it.subject }.toSet()
        val commitsInBranch2Only = commits2.filter { (_, subject) -> subject !in branch1Subjects }

        if (verbose) {
            println("Analyzing commits in '$branch2' but not '$branch1':")
            println("  Raw list: ${commitsInBranch2Only.joinToString { it.subject }}")
            println()
        }

        if (commitsInBranch2Only.isNotEmpty()) {
            println("Commits in '$branch2' but not '$branch1':")
            commitsInBranch2Only.forEach(::printCommitInfoLine)
        } else {
            println("✅ No unique commits found in '$branch2'.")
        }

        println()
        println(horizontalLine)
    }

    /** Parses a git log line to extract commit hash and subject */
    private fun parseCommitLine(commitLine: String): CommitInfo {
        val (hash, subject) = commitLine.split(Config.DELIMITER)
        if (subject.isBlank()) {
            printlnWarn("  Warning: Empty commit subject found for commit $hash.")
        }
        return CommitInfo(hash.trim('\''), normalizeSubject(subject.trim('\'')))
    }

    /** Prints a formatted commit line with hash and subject, highlighting Jewel commits */
    private fun printCommitInfoLine(commitInfo: CommitInfo) {
        val isJewelCommit = commitInfo.subject.contains("jewel", ignoreCase = true)
        if (jewelOnly && !isJewelCommit) return

        val output = buildString {
            append("  - ")
            if (showHashes) {
                append(commitInfo.hash.take(7))
                append(" | ")
            }
            append(commitInfo.subject)
        }

        if (isJewelCommit) {
            println(output.asBold())
        } else {
            println(output)
        }
    }

    /**
     * Normalizes commit subjects by removing cherry-pick prefixes. This ensures cherry-picked commits match their
     * original counterparts.
     */
    private fun normalizeSubject(subject: String): String =
        subject.replaceFirst(Regex("""^(cherry picked from commit [0-9a-f]+):? """, RegexOption.IGNORE_CASE), "").trim()
}

runBlocking { CompareBranchesCommand().main(args) }
