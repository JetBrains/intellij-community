#!/usr/bin/env kotlin
// Coroutine dependency for KTS scripts
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.exitProcess
import kotlin.time.TimeSource.Monotonic.markNow

// --- Configuration ---
object Config {
    const val UPSTREAM_REPO = "JetBrains/intellij-community"
    const val JEWEL_DIR = "."
    const val OUTPUT_FILE = "new_release_notes.md"
    const val MAX_CONCURRENT_JOBS = 5
    const val RELEASE_NOTES_FILE = "RELEASE NOTES.md"
}

// --- Data Structures ---
data class ReleaseNoteItem(val issueId: String?, val description: String, val prId: String, val prUrl: String)

enum class ProcessedPrStatus {
    Extracted,
    BlankReleaseNotes,
    NoReleaseNotes,
    Error,
}

data class CommitInfo(val commitHash: String, val prId: String, val issueId: String?)

data class CommitResult(
    val prId: String,
    val status: ProcessedPrStatus,
    val notes: Map<String, List<ReleaseNoteItem>> = emptyMap(),
    val logMessages: List<String> = emptyList(),
)

// --- Helper Functions ---
fun runCommand(vararg command: String, workDir: File = File(".")): String {
    val process =
        ProcessBuilder(*command)
            .directory(workDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

    if (!process.waitFor(60, TimeUnit.SECONDS)) {
        process.destroy()
        throw TimeoutException("Command timed out: ${command.joinToString(" ")}")
    }

    val output = process.inputStream.bufferedReader().readText()
    if (process.exitValue() != 0) {
        val error = process.errorStream.bufferedReader().readText()
        error("Command failed with exit code ${process.exitValue()}: ${command.joinToString(" ")}\n$error")
    }
    return output.trim()
}

fun formatReleaseNotesLine(note: ReleaseNoteItem): String = buildString {
    append(" *")
    if (note.issueId != null) {
        append(" **")
        append(note.issueId)
        append("**")
    }
    append(" ")
    append(note.description)
    append(" ([#")
    append(note.prId)
    append("](")
    append(note.prUrl)
    append("))")
}

// --- Core Logic (now collects logs instead of printing them) ---
fun processPr(commitInfo: CommitInfo, isVerbose: Boolean): CommitResult {
    val (_, prNumber, issueId) = commitInfo
    val logs = mutableListOf<String>()

    try {
        val prUrl =
            runCommand("gh", "pr", "view", prNumber, "--repo", Config.UPSTREAM_REPO, "--json", "url", "-q", ".url")
        val prBody =
            runCommand("gh", "pr", "view", prNumber, "--repo", Config.UPSTREAM_REPO, "--json", "body", "-q", ".body")
        if (isVerbose) logs.add("ℹ️  PR body fetched:\n${prBody.prependIndent("      ")}\n")

        val lines = prBody.lines()
        val headerIndex =
            lines.indexOfFirst { it.trim().matches("""##+\s+release notes""".toRegex(RegexOption.IGNORE_CASE)) }

        if (headerIndex == -1) {
            logs.add("⚠️ No 'Release Notes' section found.")
            return CommitResult(prNumber, ProcessedPrStatus.NoReleaseNotes, logMessages = logs)
        }

        val subsequentLines = lines.drop(headerIndex + 1)
        val nextHeaderIndex = subsequentLines.indexOfFirst { it.trim().matches("""^#{1,2}\s+.*""".toRegex()) }
        val releaseNotesText =
            (if (nextHeaderIndex != -1) subsequentLines.take(nextHeaderIndex) else subsequentLines)
                .joinToString("\n")
                .trim()

        if (releaseNotesText.isBlank()) {
            logs.add("⚠️ 'Release Notes' section found but it was empty.")
            return CommitResult(prNumber, ProcessedPrStatus.BlankReleaseNotes, logMessages = logs)
        }
        if (isVerbose) logs.add("ℹ️  Extracted release notes text:\n$releaseNotesText\n")

        val notesInPr = mutableMapOf<String, MutableList<ReleaseNoteItem>>()
        var currentSection = "Uncategorized"
        releaseNotesText.lines().forEach { line ->
            val headerMatch = """^#+\s+(.*)""".toRegex().find(line.trim())
            if (headerMatch != null) {
                currentSection =
                    headerMatch.groupValues[1].trim().lowercase().replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase() else it.toString()
                    }
            } else if (line.isNotBlank()) {
                val mainText = line.trim().removePrefix("*").removePrefix("-").trim()
                val noteItem = ReleaseNoteItem(issueId, mainText, prNumber, prUrl)
                notesInPr.getOrPut(currentSection) { mutableListOf() }.add(noteItem)
            }
        }
        logs.add("✅ Parsed notes successfully.")
        return CommitResult(prNumber, ProcessedPrStatus.Extracted, notesInPr, logs)
    } catch (e: Exception) {
        logs.add("❌ Error processing PR: ${e.message?.lines()?.firstOrNull()}")
        return CommitResult(prNumber, ProcessedPrStatus.Error, logMessages = logs)
    }
}

// --- Main Entry Point ---
val isVerbose = args.contains("--verbose") || args.contains("-v")

fun getArg(name: String, shortName: String? = null): String? {
    val nameFlag = "--$name"
    val shortNameFlag = shortName?.let { "-$it" }

    val values = args.asSequence()
        .mapIndexedNotNull { index, s ->
            if (s == nameFlag || s == shortNameFlag) {
                args.getOrNull(index + 1)
            } else {
                null
            }
        }
        .toList()
    return values.firstOrNull()
}

fun getLatestReleaseDate(): String? {
    val releaseNotesFile = File(Config.RELEASE_NOTES_FILE)
    if (!releaseNotesFile.exists()) {
        println("⚠️ Release notes file not found at '${releaseNotesFile.absolutePath}', can't determine start date.")
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
    println("⚠️ Could not find any release date in ${Config.RELEASE_NOTES_FILE}.")
    return null
}

fun printUsageAndExit() {
    println("Usage: ./extract-release-notes.main.kts --start-date <yyyy-mm-dd> [--end-date <yyyy-mm-dd>] [--verbose]")
    println("If --start-date is omitted, it will be inferred from the latest release in ${Config.RELEASE_NOTES_FILE}.")
    println("Example: ./extract-release-notes.main.kts --start-date 2025-05-01 --end-date 2025-05-31")
    exitProcess(1)
}

val startDate: String =
    getArg("start-date", "s")
        ?: getLatestReleaseDate()
        ?: run {
            printUsageAndExit()
            "" // Should be unreachable
        }
val endDate = getArg("end-date", "e")

// --- Phase 1: Sequentially parse local git history ---
val normalizedJewelPath: String = File(Config.JEWEL_DIR).normalize().absolutePath
val logMessage = buildString {
    append("🔍 Enumerating commits in '$normalizedJewelPath' since $startDate")
    if (endDate != null) {
        append(" until $endDate")
    }
}
println("$logMessage...")

val mark = markNow()
val gitLogCommand = mutableListOf("git", "log", "--since=$startDate", "--pretty=format:%H")
if (endDate != null) {
    gitLogCommand.add("--until=$endDate")
}
gitLogCommand.add("--")
gitLogCommand.add(Config.JEWEL_DIR)

val allCommitHashes =
    runCommand(*gitLogCommand.toTypedArray())
        .lines()
        .filter { it.isNotBlank() }

val elapsed = mark.elapsedNow()

println("  Found ${allCommitHashes.size} commits in $elapsed")

val prCommits = mutableListOf<CommitInfo>()
val issueIdRegex = """\[(JEWEL-\d+)]""".toRegex()
val prRegex = """closes https://github.com/JetBrains/intellij-community/pull/(\d+)""".toRegex()

for (commitHash in allCommitHashes) {
    val commitBody = runCommand("git", "show", "-s", "--format=%B", commitHash)
    prRegex.find(commitBody)?.groups?.get(1)?.value?.let { prNumber ->
        val issueId = issueIdRegex.find(commitBody)?.groups?.get(1)?.value
        prCommits.add(CommitInfo(commitHash, prNumber, issueId))
    }
}

val uniquePrCommits = prCommits.distinctBy { it.prId }

println(
    "  Found ${uniquePrCommits.size} unique PRs to process. " +
        "(${allCommitHashes.size - uniquePrCommits.size} commits were skipped or were duplicates)"
)

if (isVerbose) {
    for (commitInfo in uniquePrCommits) {
        val issueId = commitInfo.issueId ?: "unknown"
        println("    Commit ${commitInfo.commitHash} -> PR #${commitInfo.prId}, issue $issueId")
    }
}

// --- Phase 2: Process all PRs in parallel ---
println("\n🔎 Processing ${uniquePrCommits.size} PRs with up to ${Config.MAX_CONCURRENT_JOBS} parallel jobs...")

@Suppress("RAW_RUN_BLOCKING") // This is not IJP code
val results = runBlocking {
    val dispatcher = Dispatchers.IO.limitedParallelism(Config.MAX_CONCURRENT_JOBS)
    val inProgressPrs = ConcurrentHashMap.newKeySet<String>()

    // Launch a separate logger coroutine to print progress
    val loggerJob = launch {
        while (isActive) {
            val currentPrs = inProgressPrs.map { "#$it" }.sorted().joinToString(", ")
            print("\r  Currently processing: [${currentPrs.padEnd(50)}]")
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
    print("\r".padEnd(80) + "\r") // Clear the progress line completely
    println("\n✅ All PRs have been processed.")
    completedResults
}

// 3. Aggregate final results
val allReleaseNotes = mutableMapOf<String, MutableList<ReleaseNoteItem>>()
val processedPrs = mutableMapOf<String, ProcessedPrStatus>()

results.forEach { result ->
    processedPrs[result.prId] = result.status
    result.notes.forEach { (section, items) -> allReleaseNotes.getOrPut(section) { mutableListOf() }.addAll(items) }
}

// --- NEW: Print collated logs ---
println("\n--- PROCESSING LOGS ---")

results
    .sortedBy { it.prId.toInt() }
    .forEach { result ->
        println("\n[PR #${result.prId}]")
        result.logMessages.forEach { msg -> println("  $msg") }
    }

// 4. Write grouped release notes to file
println("\n\n✍️ Writing release notes to ${Config.OUTPUT_FILE}...")

val outputFile = File(Config.OUTPUT_FILE)

outputFile.writeText("")

val sectionOrder = listOf("New Features", "Enhancements", "Bug Fixes", "Deprecations", "Uncategorized")
val sortedSections =
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

val summaryData = processedPrs.entries.groupBy({ it.value }, { it.key })

ProcessedPrStatus.entries.forEach { status ->
    val prs = summaryData[status]?.map { it.toInt() }?.sorted() ?: emptyList()
    if (prs.isEmpty()) return@forEach
    println("\n[${status.name}] - ${prs.size} PRs")
    println(prs.joinToString(", ") { "#$it" })
}

println("\n\n✅  All tasks complete.")
