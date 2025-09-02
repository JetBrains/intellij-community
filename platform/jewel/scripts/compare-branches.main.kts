#!/usr/bin/env kotlin
@file:Suppress("RAW_RUN_BLOCKING")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:5.0.3")
@file:Import("utils.main.kts") // Assuming utils.main.kts is needed for runCommand and getLatestReleaseDate

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import java.io.File

private object Config {
    val PATHS_TO_CHECK =
        listOf(
            "platform/jewel",
            "libraries/skiko",
            "libraries/compose-foundation-desktop-junit",
            "libraries/detekt-compose-rules",
            "libraries/compose-foundation-desktop",
            "build",
        )
    const val DELIMITER = "::COMMIT::"
    const val GIT_LOG_FORMAT = "%H${DELIMITER}%s"
}

private data class CommitInfo(
    val hash: String,
    val subject: String,
)

class CompareBranchesCommand : SuspendingCliktCommand() {
    private val verbose: Boolean by
        option("--verbose", help = "Prints the normalized commit subjects being compared.").flag(default = false)
    private val jewelOnly: Boolean by
        option("--jewel-only", help = "Only prints commits whose subject contains the word 'jewel' (case insensitive).")
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

        if (!isDirectoryGitRepo(communityRoot)) {
            printlnErr("Error: Not a git repository.")
            exitProcess(1)
        }

        if (!branchExists(branch1, communityRoot)) {
            printlnErr("Error: Branch '$branch1' not found.")
            exitProcess(1)
        }

        if (!branchExists(branch2, communityRoot)) {
            printlnErr("Error: Branch '$branch2' not found.")
            exitProcess(1)
        }

        // Ensure all paths to check exist
        var anyPathMissing = false
        for (path in Config.PATHS_TO_CHECK) {
            val file = File(communityRoot, path)
            if (!file.isDirectory()) {
                printlnErr("Error: Path to check does not exist: $path")
                anyPathMissing = true
            }
        }
        if (anyPathMissing) exitProcess(1)

        println(
            "🔍 Comparing commits for paths '${Config.PATHS_TO_CHECK.joinToString()}' between " +
            "'$branch1' and '$branch2' since $sinceDate..."
        )

        val pathsArg = Config.PATHS_TO_CHECK.joinToString(separator = " ") { "-- $it" }

        val gitLogCommand1 = "git log $branch1 --after=$sinceDate --pretty=format:'${Config.GIT_LOG_FORMAT}' $pathsArg"
        val commits1 =
            runCommand(gitLogCommand1, communityRoot)
                .output
                .lines()
                .filter { it.isNotBlank() }
                .map { parseCommitLine(it) }

        val gitLogCommand2 = "git log $branch2 --after=$sinceDate --pretty=format:'${Config.GIT_LOG_FORMAT}' $pathsArg"
        val commits2 =
            runCommand(gitLogCommand2, communityRoot)
                .output
                .lines()
                .filter { it.isNotBlank() }
                .map { parseCommitLine(it) }

        if (verbose) {
            if (commits1.isNotEmpty()) {
                println("\n--- Commits for '$branch1' ---")
                commits1.forEach(::printCommitInfoLine)
                println()
            }
            if (commits2.isNotEmpty()) {
                println("\n--- Commits for '$branch2' ---")
                commits2.forEach(::printCommitInfoLine)
                println()
            }
        }

        val horizontalLine = "------------------------------------------------------------------"
        println(horizontalLine)

        val branch2Subjects = commits2.map { it.subject }.toSet()
        val commitsInBranch1Only = commits1.filter { (_, subject) -> subject !in branch2Subjects }

        if (verbose) {
            println("Commits in '$branch1' but not '$branch2':")
            println(commitsInBranch1Only.joinToString("\n"))
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

        val branch1Subjects = commits1.map { it.subject }.toSet()
        val commitsInBranch2Only = commits2.filter { (_, subject) -> subject !in branch1Subjects }

        if (verbose) {
            println("All commits in '$branch2' but not '$branch1':")
            println(commitsInBranch2Only.joinToString("\n"))
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

    private fun parseCommitLine(commitLine: String): CommitInfo {
        val (hash, subject) = commitLine.split(Config.DELIMITER)
        if (subject.isBlank()) {
            printlnWarn("  Warning: Empty commit subject found for commit $hash.")
        }
        return CommitInfo(hash.trim('\''), normalizeSubject(subject.trim('\'')))
    }

    private fun printCommitInfoLine(commitInfo: CommitInfo) {
        val isJewelCommit = commitInfo.subject.contains("jewel", ignoreCase = true)
        if (jewelOnly && !isJewelCommit) return

        val output = buildString {
            append("  - ")
            append(commitInfo.hash.take(7))
            append(" | ")
            append(commitInfo.subject)
        }

        if (isJewelCommit) {
            println(output.asBold())
        } else {
            println(output)
        }
    }

    private suspend fun isDirectoryGitRepo(directory: File): Boolean =
        runCommand("git rev-parse --is-inside-work-tree", directory).isSuccess

    private suspend fun branchExists(branch: String, directory: File): Boolean =
        runCommand("git rev-parse --verify $branch", directory).isSuccess

    private fun normalizeSubject(subject: String): String =
        subject
            .replaceFirst(Regex("""^(cherry picked from commit [0-9a-f]+):? """, RegexOption.IGNORE_CASE), "")
            .trim()
}

runBlocking { CompareBranchesCommand().main(args) }
