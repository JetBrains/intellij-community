#!/usr/bin/env kotlin
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")
@file:Import("utils.main.kts")
@file:Suppress("RAW_RUN_BLOCKING")

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess
import kotlin.time.TimeSource.Monotonic.markNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// --- Configuration ---
private object Config {
    const val UPSTREAM_REPO = "JetBrains/intellij-community"
    const val JEWEL_DIR = "."
    const val OUTPUT_FILE = "new_release_notes.md"
    const val MAX_CONCURRENT_JOBS = 7
    const val RELEASE_NOTES_FILE = "RELEASE NOTES.md"
}

private val workingDir = File("").absoluteFile

// --- Data Structures ---
private data class ReleaseNoteItem(val issueId: String?, val description: String, val prId: String, val prUrl: String)

private enum class PrProcessingStatus {
    Extracted,
    BlankReleaseNotes,
    NoReleaseNotes,
    Error,
}

private data class CommitInfo(val commitHash: String, val prId: String, val issueId: String?)

private data class PrProcessingResult(
    val prId: String,
    val prTitle: String,
    val status: PrProcessingStatus,
    val notes: Map<String, List<ReleaseNoteItem>> = emptyMap(),
    val logMessages: List<String> = emptyList(),
)

// --- Helper Functions ---
private fun getIndentation(line: String): Int = line.takeWhile { it.isWhitespace() }.length

private fun formatReleaseNotesLine(note: ReleaseNoteItem): String {
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

private fun String.cleanupEntry(issueIdText: String?): String {
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

private suspend fun processPr(commitInfo: CommitInfo, isVerbose: Boolean): PrProcessingResult {
    val (_, prNumber, issueId) = commitInfo
    val logs = mutableListOf<String>()

    try {
        val prInfo =
            runCommand("gh pr view $prNumber --repo ${Config.UPSTREAM_REPO} --json url,body,title", workingDir)
                .output
                .let { Json.parseToJsonElement(it).jsonObject }

        val prUrl = prInfo["url"]?.jsonPrimitive?.content!!
        val prBody = prInfo["body"]?.jsonPrimitive?.content!!
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

private fun processLine(
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

// --- Main Entry Point ---
if (workingDir.name != "jewel" || workingDir.parentFile.name != "platform") {
    printlnErr("This script must be run from the 'jewel' directory.")
    exitProcess(1)
}

private fun getLatestReleaseDate(): String? {
    val releaseNotesFile = File(Config.RELEASE_NOTES_FILE)
    if (!releaseNotesFile.exists()) {
        printlnWarn(
            "⚠️ Release notes file not found at '${releaseNotesFile.absolutePath}', can't determine start date."
        )
        return null
    }

    val releaseHeaderRegex = """## v\d+\.\d+ \((....-..-..)\)""".toRegex()
    releaseNotesFile.useLines { lines ->
        for (line in lines) {
            val match = releaseHeaderRegex.find(line)
            if (match != null) {
                return match.groupValues[1]
            }
        }
    }
    printlnWarn("⚠️ Could not find any release date in ${Config.RELEASE_NOTES_FILE}.")
    return null
}

private fun printUsageAndExit() {
    println("Usage: ./extract-release-notes.main.kts --start-date <yyyy-mm-dd> [--end-date <yyyy-mm-dd>] [--verbose]")
    println("If --start-date is omitted, it will be inferred from the latest release in ${Config.RELEASE_NOTES_FILE}.")
    println("Example: ./extract-release-notes.main.kts --start-date 2025-05-01 --end-date 2025-05-31")
    exitProcess(1)
}

private val startDate: String =
    getArg("start-date", "s")
        ?: getLatestReleaseDate()
        ?: run {
            printUsageAndExit()
            "" // Should be unreachable
        }
private val endDate = getArg("end-date", "e")

// --- Phase 1: Sequentially parse local git history ---
private val normalizedJewelPath: String = File(Config.JEWEL_DIR).normalize().canonicalPath
private val logMessage = buildString {
    append("🔍 Enumerating commits in '$normalizedJewelPath' since $startDate")
    if (endDate != null) {
        append(" until $endDate")
    }
}

print("$logMessage...")

private val mark = markNow()
private val gitLogCommand = buildString {
    append("git log --since=")
    append(startDate)
    append(" --pretty=format:%H")
    if (endDate != null) {
        append(" --until=$endDate")
    }
    append(" -- ")
    append(Config.JEWEL_DIR)
}

private val allCommitHashes = runBlocking {
    runCommand(gitLogCommand, workingDir).output.lines().filter { it.isNotBlank() }
}

private val elapsed = mark.elapsedNow()

println(" DONE")

println("  ℹ️ Found ${allCommitHashes.size} commits in $elapsed")

print("🔍 Filtering relevant commits...")

private val prCommits = mutableListOf<CommitInfo>()
private val jewelCommitsWithoutPr = mutableListOf<Pair<String, String>>()
private val issueIdRegex = """\[(JEWEL-\d+.*)+]""".toRegex()
private val prRegex = """closes https://github.com/JetBrains/intellij-community/pull/(\d+)""".toRegex()

for (commitHash in allCommitHashes) {
    val commitBody = runBlocking { runCommand("git show -s --format=%B $commitHash", workingDir).output }

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

private val uniquePrCommits = prCommits.distinctBy { it.prId }.sortedBy { it.issueId }

println(" DONE")

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
println("🔎 Processing ${uniquePrCommits.size} PRs with up to ${Config.MAX_CONCURRENT_JOBS} parallel jobs...")

@OptIn(ExperimentalCoroutinesApi::class)
private val results = runBlocking {
    val dispatcher = Dispatchers.IO.limitedParallelism(Config.MAX_CONCURRENT_JOBS)
    val inProgressPrs = ConcurrentHashMap.newKeySet<String>()

    // Launch a separate logger coroutine to print progress
    val loggerJob = launch {
        while (isActive) {
            val currentPrs = inProgressPrs.sorted().joinToString(", ") { "#$it" }
            val terminalWidth = getTerminalWidth()
            val maxLen = terminalWidth - 20 // Hardcoded to include the "chrome"
            print("\u001B[2K  ⏳ Processing: [${currentPrs.take(maxLen).padEnd(maxLen)}]\r")
            delay(100)
        }
    }

    val jobs =
        uniquePrCommits.map { commitInfo ->
            async(dispatcher) {
                inProgressPrs.add(commitInfo.prId)
                try {
                    processPr(commitInfo, isVerbose)
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
private val allReleaseNotes = mutableMapOf<String, MutableList<ReleaseNoteItem>>()
private val processedPrs = mutableMapOf<String, PrProcessingResult>()

results.forEach { result ->
    processedPrs[result.prId] = result
    result.notes.forEach { (section, items) -> allReleaseNotes.getOrPut(section) { mutableListOf() }.addAll(items) }
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
println("✍️ Writing release notes to ${Config.OUTPUT_FILE}...")

private val outputFile = File(Config.OUTPUT_FILE)

outputFile.writeText("")

private val sectionOrder = listOf("⚠️ Important Changes", "New features", "Bug fixes", "Deprecated API", "Other")
private val sortedSections =
    allReleaseNotes.keys.sortedWith(
        compareBy { sectionKey -> sectionOrder.indexOf(sectionKey).let { if (it == -1) Int.MAX_VALUE else it } }
    )

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

private val summaryData: Map<PrProcessingStatus, List<PrProcessingResult>> =
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
