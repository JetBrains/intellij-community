@file:Suppress("IO_FILE_USAGE", "RAW_RUN_BLOCKING")

package org.jetbrains.jewel.scripts.bazel

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource.Monotonic.markNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val UPSTREAM_REPO = "JetBrains/intellij-community"
private const val OUTPUT_FILE = "new_release_notes.md"
private const val MAX_CONCURRENT_JOBS = 7

// Run it like: bazel run //platform/jewel/build-scripts/bazel:extractReleaseNotes
fun main(args: Array<String>) {
    runBlocking { ExtractReleaseNotesCommand().main(args) }
}

internal class ExtractReleaseNotesCommand(private val commandRunner: CommandRunner = DefaultCommandRunner) :
    SuspendingCliktCommand(name = "extract-release-notes") {

    private val startDate: String by
        option(
                "--start-date",
                "-s",
                "--since",
                help =
                    "The start date for the commit range (yyyy-mm-dd). " +
                        "If omitted, it will be inferred from the latest release in RELEASE NOTES.md.",
            )
            .defaultLazy {
                val latestReleaseDate = getLatestReleaseDate(getJewelRoot() ?: File(".").canonicalFile)
                if (latestReleaseDate.isNullOrBlank()) {
                    throw UsageError(
                        "Error: --start-date is required if RELEASE NOTES.md does not exist or contain a release date."
                    )
                }
                latestReleaseDate
            }

    private val endDate: String? by
        option(
            "--end-date",
            "-e",
            "--until",
            help = "The end date for the commit range (yyyy-mm-dd). If omitted, it will default to today.",
        )

    private val isVerbose: Boolean by option("--verbose", "-v", help = "Enables verbose logging.").flag(default = false)

    override fun help(context: Context): String =
        "Extracts release notes from PRs merged within a specified date range."

    override suspend fun run() {
        print("⏳ Locating Jewel root...")
        val jewelRoot = getJewelRoot() ?: exitWithError("Could not find the Jewel root directory.")
        println(" DONE: ${jewelRoot.canonicalPath}")

        requireGhTool(commandRunner)

        // --- Phase 1: Sequentially parse local git history ---
        val logMessage = buildString {
            append("🔍 Enumerating commits in '${jewelRoot.canonicalPath}' since $startDate")
            if (endDate != null) {
                append(" until $endDate")
            }
        }

        print("$logMessage...")

        val mark = markNow()
        val gitLogCommand = buildString {
            append("git log --since=")
            append(startDate)
            append(" --pretty=format:%H")
            if (endDate != null) {
                append(" --until=$endDate")
            }
            append(" -- .")
        }

        val allCommitHashes = commandRunner(gitLogCommand, jewelRoot).output.lines().filter { it.isNotBlank() }

        val elapsed = mark.elapsedNow()

        printlnSuccess(" DONE")

        println("  ℹ️ Found ${allCommitHashes.size} commits in $elapsed")

        print("🔍 Filtering relevant commits...")

        val prCommits = mutableListOf<CommitInfo>()
        val jewelCommitsWithoutPr = mutableListOf<Pair<String, String>>()
        val issueIdRegex = """\[(JEWEL-\d+.*)+]""".toRegex()
        val prRegex = """closes https://github.com/JetBrains/intellij-community/pull/(\d+)""".toRegex()

        for (commitHash in allCommitHashes) {
            val commitBody = commandRunner("git show -s --format=%B $commitHash", jewelRoot).output

            val prNumber = prRegex.find(commitBody)?.groups?.get(1)?.value
            if (prNumber != null) {
                if (isVerbose) {
                    println("    Commit $commitHash -> PR #$prNumber")
                }
                val issueId = issueIdRegex.find(commitBody)?.groups?.get(1)?.value
                prCommits.add(CommitInfo(commitHash, prNumber, issueId))
            } else {
                if (commitBody.contains("JEWEL", ignoreCase = true)) {
                    jewelCommitsWithoutPr.add(commitHash to commitBody.lineSequence().first())
                }
                if (isVerbose) {
                    println("    Commit $commitHash -> NO PR")
                }
            }
        }

        val uniquePrCommits = prCommits.distinctBy { it.prId }.sortedBy { it.issueId }

        printlnSuccess(" DONE")

        println(
            "  ℹ️ Found ${uniquePrCommits.size} unique PRs to process. " +
                "(${allCommitHashes.size - uniquePrCommits.size} commits were skipped or were duplicates)"
        )

        if (isVerbose) {
            for (commitInfo in uniquePrCommits) {
                val issueId = commitInfo.issueId ?: "unknown"
                println("    Commit ${commitInfo.commitHash} -> PR #${commitInfo.prId}, issue $issueId")
            }
        }

        // --- Phase 2: Process all PRs in parallel ---
        println("🔎 Processing ${uniquePrCommits.size} PRs with up to $MAX_CONCURRENT_JOBS parallel jobs...")

        val results = coroutineScope {
            val dispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_JOBS)
            val inProgressPrs = ConcurrentHashMap.newKeySet<String>()

            // Launch a separate logger coroutine to print progress
            val loggerJob = launch {
                while (isActive) {
                    val currentPrs = inProgressPrs.sorted().joinToString(", ") { "#$it" }
                    val terminalWidth = getTerminalWidth()
                    val maxLen = terminalWidth - 20 // Hardcoded to include the "chrome"
                    print("\u001B[2K  ⏳ Processing: [${currentPrs.take(maxLen).padEnd(maxLen)}]\r")
                    delay(100.milliseconds)
                }
            }

            val jobs =
                uniquePrCommits.map { commitInfo ->
                    async(dispatcher) {
                        inProgressPrs.add(commitInfo.prId)
                        try {
                            processPr(commitInfo, isVerbose, jewelRoot, commandRunner)
                        } finally {
                            inProgressPrs.remove(commitInfo.prId)
                        }
                    }
                }

            val completedResults = jobs.awaitAll()
            loggerJob.cancel()
            print("\r\u001B[2K") // Clear the progress line completely
            println("  ✅ All PRs have been processed.")
            completedResults
        }

        // 3. Aggregate final results
        val allReleaseNotes = mutableMapOf<String, MutableList<ReleaseNoteItem>>()
        val processedPrs = mutableMapOf<String, PrProcessingResult>()

        results.forEach { result ->
            processedPrs[result.prId] = result
            result.notes.forEach { (section, items) ->
                allReleaseNotes
                    .getOrPut(section) { mutableListOf() }
                    .addAll(items.filter { it.description.isNotBlank() })
            }
        }

        // --- Print collated logs ---
        if (isVerbose) {
            println("\n--- PROCESSING LOGS ---")

            results
                .sortedBy { it.prId.toInt() }
                .forEach { result ->
                    println("\n[PR #${result.prId}]")
                    result.logMessages.forEach { msg -> println("  $msg") }
                }

            println()
        }

        // 4. Write grouped release notes to the output file
        println("✍️ Writing release notes to $OUTPUT_FILE...")

        val outputFile = File(OUTPUT_FILE)

        outputFile.writeText("")

        val sectionOrder = listOf("⚠️ Important Changes", "New features", "Bug fixes", "Deprecated API", "Other")
        val sortedSections =
            allReleaseNotes.keys.sortedBy { sectionKey ->
                sectionOrder.indexOf(sectionKey).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }

        sortedSections.forEach { sectionHeader ->
            val notes = allReleaseNotes[sectionHeader]!!
            outputFile.appendText("### $sectionHeader\n\n")
            notes.forEach { note ->
                val formattedLine = formatReleaseNotesLine(note)
                outputFile.appendText("$formattedLine\n")
            }
            outputFile.appendText("\n")
        }

        println("  ✅ Done.")

        // 5. Final Summary Table
        println("\n--- SUMMARY ---")

        val summaryData: Map<PrProcessingStatus, List<PrProcessingResult>> =
            processedPrs.entries.groupBy({ it.value.status }, { it.value })

        PrProcessingStatus.entries.forEach { status ->
            val processingResults = summaryData[status] ?: return@forEach
            val prs = processingResults.sortedBy { it.prId }
            println("\n[${status.name}] - ${prs.size} PRs")
            for (pr in prs) {
                print(" * ")
                val id = pr.prId
                println("#$id — ${pr.prTitle}".asLink("https://github.com/JetBrains/intellij-community/pull/$id"))
            }
        }

        println("\n\n✅  All tasks complete.")

        if (jewelCommitsWithoutPr.isNotEmpty()) {
            println()
            printlnWarn("⚠️ Found ${jewelCommitsWithoutPr.size} commits with 'JEWEL' in the message but no PR number:")

            for ((commitHash, headerLine) in jewelCommitsWithoutPr) {
                println("  * ${commitHash.take(7)} ${headerLine}")
            }
            println()
        }
    }
}

internal data class ReleaseNoteItem(val issueId: String?, val description: String, val prId: String, val prUrl: String)

internal enum class PrProcessingStatus {
    Extracted,
    BlankReleaseNotes,
    NoReleaseNotes,
    Error,
}

internal data class CommitInfo(val commitHash: String, val prId: String, val issueId: String?)

internal data class PrProcessingResult(
    val prId: String,
    val prTitle: String,
    val status: PrProcessingStatus,
    val notes: Map<String, List<ReleaseNoteItem>> = emptyMap(),
    val logMessages: List<String> = emptyList(),
)

internal fun getIndentation(line: String): Int = line.takeWhile { it.isWhitespace() }.length

internal fun formatReleaseNotesLine(note: ReleaseNoteItem): String {
    val lines = note.description.lines()
    val firstLine = lines.first()
    val otherLines = lines.drop(1)

    return buildString {
        append(" *")
        if (note.issueId != null) {
            append(" **")
            append(note.issueId)
            append("**")
        }
        append(" ")
        append(firstLine.cleanupEntry(note.issueId))
        append(" ([#")
        append(note.prId)
        append("](")
        append(note.prUrl)
        append("))")

        if (otherLines.isNotEmpty()) {
            val otherLinesText = otherLines.joinToString("\n")
            if (otherLinesText.isNotBlank()) {
                append("\n")
                append(otherLinesText)
            }
        }
    }
}

internal fun String.cleanupEntry(issueIdText: String?): String {
    // 1. Remove trailing dot
    val step1 = removeSuffix(".")
    // 2. Remove issue ID if present
    val step2 =
        if (issueIdText != null) {
            step1.removePrefix("$issueIdText ").removePrefix("**$issueIdText** ")
        } else {
            step1
        }
    // 3. Trim
    return step2.trim()
}

internal suspend fun processPr(
    commitInfo: CommitInfo,
    isVerbose: Boolean,
    jewelRoot: File,
    commandRunner: CommandRunner,
): PrProcessingResult {
    val (_, prNumber, issueId) = commitInfo
    val logs = mutableListOf<String>()

    try {
        val prInfo =
            commandRunner("gh pr view $prNumber --repo $UPSTREAM_REPO --json url,body,title", jewelRoot).output.let {
                Json.parseToJsonElement(it).jsonObject
            }

        val prUrl = prInfo["url"]?.jsonPrimitive?.content!!
        val prBody = prInfo["body"]?.jsonPrimitive?.content!!.substringBefore("<!-- CURSOR_SUMMARY -->")
        val prTitle = prInfo["title"]?.jsonPrimitive?.content!!
        if (isVerbose) logs.add("ℹ️  PR body fetched:\n${prBody.prependIndent("      ")}\n")

        val lines = prBody.lines()
        val headerIndex =
            lines.indexOfFirst { it.trim().matches("""##+\s+release notes""".toRegex(RegexOption.IGNORE_CASE)) }

        if (headerIndex == -1) {
            logs.add("⚠️ No 'Release Notes' section found.".asWarning())
            return PrProcessingResult(prNumber, prTitle, PrProcessingStatus.NoReleaseNotes, logMessages = logs)
        }

        val subsequentLines = lines.drop(headerIndex + 1)
        val nextHeaderIndex = subsequentLines.indexOfFirst { it.trim().matches("""^#{1,2}\s+.*""".toRegex()) }
        val releaseNotesText =
            (if (nextHeaderIndex != -1) subsequentLines.take(nextHeaderIndex) else subsequentLines)
                .joinToString("\n")
                .trim()

        if (releaseNotesText.isBlank()) {
            logs.add("⚠️ 'Release Notes' section found but it was empty.".asWarning())
            return PrProcessingResult(prNumber, prTitle, PrProcessingStatus.BlankReleaseNotes, logMessages = logs)
        }
        if (isVerbose) logs.add("ℹ️  Extracted release notes text:\n$releaseNotesText\n")

        val notesInPr = mutableMapOf<String, MutableList<ReleaseNoteItem>>()
        var currentSection = "Other"
        val releaseLines = releaseNotesText.lines()

        var i = 0
        while (i < releaseLines.size) {
            val (nextIndex, nextSection) =
                processLine(i, releaseLines, currentSection, issueId, prNumber, prUrl, notesInPr)
            i = nextIndex
            currentSection = nextSection
        }

        logs.add("✅ Parsed notes successfully.")
        return PrProcessingResult(prNumber, prTitle, PrProcessingStatus.Extracted, notesInPr, logs)
    } catch (e: Exception) {
        logs.add("❌ Error processing PR: ${e.message?.lines()?.firstOrNull()}".asError())
        return PrProcessingResult(prNumber, "[ERROR]", PrProcessingStatus.Error, logMessages = logs)
    }
}

internal fun processLine(
    index: Int,
    releaseLines: List<String>,
    currentSectionIn: String,
    issueId: String?,
    prNumber: String,
    prUrl: String,
    notesInPr: MutableMap<String, MutableList<ReleaseNoteItem>>,
): Pair<Int, String> {
    var currentSection = currentSectionIn
    val line = releaseLines[index]

    val headerMatch = """^#+\s+(.*)""".toRegex().find(line.trim())
    if (headerMatch != null) {
        currentSection = headerMatch.groupValues[1].trim()
        return index + 1 to currentSection
    }

    if (line.isBlank()) {
        return index + 1 to currentSection
    }

    val trimmedLine = line.trim()
    val isListItem = trimmedLine.startsWith("*") || trimmedLine.startsWith("-")

    if (isListItem) {
        val baseIndentation = getIndentation(line)
        val mainText = trimmedLine.removePrefix("*").removePrefix("-").trim()
        val noteLines = mutableListOf(mainText)

        var j = index + 1
        while (j < releaseLines.size) {
            val nextLine = releaseLines[j]
            if (nextLine.isNotBlank()) {
                if ("""^#+\s+(.*)""".toRegex().find(nextLine.trim()) != null) break // Stop at next header
                if (getIndentation(nextLine) <= baseIndentation) break // Stop at new top-level item
            }

            noteLines.add(nextLine)
            j++
        }

        val fullDescription = noteLines.joinToString("\n")
        val noteItem = ReleaseNoteItem(issueId, fullDescription, prNumber, prUrl)
        notesInPr.getOrPut(currentSection) { mutableListOf() }.add(noteItem)
        return j to currentSection
    } else {
        // This line is not a list item, so we skip it.
        return index + 1 to currentSection
    }
}
