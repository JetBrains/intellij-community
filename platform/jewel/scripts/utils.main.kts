@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:5.0.3")
@file:DependsOn("com.github.pgreze:kotlin-process:1.5.1")
@file:Suppress("RAW_RUN_BLOCKING", "VariableNaming")

import com.github.ajalt.clikt.core.PrintMessage
import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.intellij.lang.annotations.Language
import java.io.File

private val JEWEL_MARKER_FILE_NAME = "JEWEL_MARKER"
private val COMMUNITY_ROOT_MARKER_FILE_NAME = ".community.root.marker"
private val COMMUNITY_IML_FILE_NAME = "intellij.idea.community.main.iml"

private val CONTROL_CODE = '\u001b'
private val CODE_SUCCESS = "$CONTROL_CODE[0;32m"
private val CODE_ERR = "$CONTROL_CODE[0;31m"
private val CODE_WARN = "$CONTROL_CODE[0;33m"
private val CODE_BOLD = "$CONTROL_CODE[1m"
private val CODE_CLEAR = "$CONTROL_CODE[0m"

/**
 * Checks if the GitHub CLI tool (`gh`) is present on the system's PATH.
 *
 * @return `true` if the `gh` tool is found, `false` otherwise.
 */
fun checkGhTool() = runBlocking { runCommand(command = "which gh", workingDir = null, exitOnError = false).isSuccess }

/**
 * Checks if the PR number environment variable (`PR_NUMBER`) is set and is a valid integer.
 *
 * @return `true` if `PR_NUMBER` is set and is an integer, `false` otherwise.
 */
fun checkPrNumber() = System.getenv("PR_NUMBER")?.trim()?.toIntOrNull() != null

/**
 * Gets the value of the PR number environment variable (`PR_NUMBER`).
 *
 * @return The PR number as a string.
 * @throws IllegalStateException if the `PR_NUMBER` environment variable is not set.
 */
fun getPrNumber() = checkNotNull(System.getenv("PR_NUMBER")?.trim()) { "PR number not set" }

/**
 * Requires the GitHub CLI tool (`gh`) to be present on the system's PATH. Exits the process with an error code if the
 * tool is not found.
 */
fun requireGhTool() {
    if (checkGhTool()) return

    throw PrintMessage("ERROR: the GitHub CLI tool must be present on the PATH.", printError = true)
}

/**
 * Recursively searches upwards from a given base directory to find the "jewel" directory indicated by the presence of
 * the [JEWEL_MARKER_FILE_NAME].
 *
 * @param base The starting directory for the search. Defaults to the current working directory.
 * @return The canonical [File] of the "jewel" directory if found, otherwise `null`.
 */
fun findJewelRoot(base: File = File(".").canonicalFile): File? =
    findDir(base) {
        val marker = it.resolve(JEWEL_MARKER_FILE_NAME)
        marker.isFile
    } ?: findCommunityRoot(base)?.resolve("platform/jewel")?.takeIf { it.isDirectory }

/**
 * Finds the root directory of the IntelliJ Community project by recursively searching upwards from a given base
 * directory, verifying it contains the community marker file ([COMMUNITY_ROOT_MARKER_FILE_NAME]) and the community iml
 * file ([COMMUNITY_IML_FILE_NAME]).
 *
 * @param base The starting directory for the search. Defaults to the current working directory.
 * @return The [File] of the community directory if found, otherwise `null`.
 */
fun findCommunityRoot(base: File = File("").canonicalFile): File? =
    findDir(base) { it.resolve(COMMUNITY_ROOT_MARKER_FILE_NAME).isFile && it.resolve(COMMUNITY_IML_FILE_NAME).isFile }

private fun findDir(base: File, isDesiredDir: (File) -> Boolean): File? {
    var current = base
    while (true) {
        if (!current.canRead()) {
            printlnErr("Directory is not readable, stopping search: ${current.absolutePath}")
            return null
        }

        if (isDesiredDir(current)) {
            return current.canonicalFile
        }

        current = current.parentFile ?: return null
    }
}

/**
 * Requires the PR number environment variable (`PR_NUMBER`) to be set and be a valid integer. Exits the process with an
 * error code if the variable is not set or is not an integer.
 */
fun requirePrNumber() {
    if (checkPrNumber()) return

    throw PrintMessage("ERROR: PR_NUMBER environment variable not set.", printError = true)
}

/**
 * Prints an error message to the standard error stream with error formatting.
 *
 * @param message The error message to print.
 */
fun printlnErr(message: String) {
    System.err.println(message.asError())
}

/**
 * Prints a warning message to the standard error stream with warning formatting.
 *
 * @param message The warning message to print.
 */
fun printlnWarn(message: String) {
    System.err.println(message.asWarning())
}

/**
 * Prints a success message to the standard output stream with success (green) formatting.
 *
 * @param message The success message to print.
 */
fun printlnSuccess(message: String) {
    println(message.asSuccess())
}

/**
 * Formats a string as a warning message with ANSI escape codes.
 *
 * @return The formatted text, or the text itself if the terminal does not support styling.
 */
fun String.asWarning() = if (stylingSupported) "$CODE_WARN$this$CODE_CLEAR" else this

/**
 * Formats a string as an error message with ANSI escape codes if the terminal supports it.
 *
 * @return The formatted text, or the text itself if the terminal does not support styling.
 */
fun String.asError() = if (stylingSupported) "$CODE_ERR$this$CODE_CLEAR" else this

/**
 * Formats a string as a bold text with ANSI escape codes if the terminal supports it.
 *
 * @return The formatted text, or the text itself if the terminal does not support styling.
 */
fun String.asBold(): String = if (stylingSupported) "$CODE_BOLD$this$CODE_CLEAR" else this

/**
 * Formats a string as a success (green) text with ANSI escape codes if the terminal supports it.
 *
 * @return The formatted text, or the text itself if the terminal does not support styling.
 */
fun String.asSuccess(): String = if (stylingSupported) "$CODE_SUCCESS$this$CODE_CLEAR" else this

/**
 * Detects if the current terminal likely supports OSC 8 hyperlinks by checking for known environment variables.
 *
 * @return `true` if a compatible terminal is detected, `false` otherwise.
 */
private fun doesTerminalSupportHyperlinks(): Boolean {
    // Check for iTerm, VSCode, Hyper, WezTerm, etc.
    val termProgram = System.getenv("TERM_PROGRAM")
    if (termProgram != null) {
        return when (termProgram) {
            "iTerm.app",
            "vscode",
            "WezTerm",
            "Hyper" -> true
            else -> false
        }
    }

    // Check for VTE-based terminals (GNOME Terminal, Tilix)
    // Support was added in version 0.50 -> 5000
    val vteVersion = System.getenv("VTE_VERSION")?.toIntOrNull()
    if (vteVersion != null && vteVersion >= 5000) {
        return true
    }

    // Check for IntelliJ's built-in terminal
    if (System.getenv("TERMINAL_EMULATOR") == "JetBrains-JediTerm") {
        return true
    }

    // Fallback if no known terminal is detected
    return false
}

private val stylingSupported = doesTerminalSupportStyling()

/**
 * Detects if the current terminal supports styling (colors, bold, etc.).
 *
 * @return `true` if a compatible terminal is detected, `false` otherwise.
 */
private fun doesTerminalSupportStyling(): Boolean =
    System.getenv("TERM")?.contains("color") == true || System.getenv("COLORTERM") == "truecolor"

/**
 * Formats a string as a hyperlink using OSC 8 escape codes if the terminal supports it.
 *
 * @param url The URL for the hyperlink.
 * @return The formatted string with the hyperlink, or the original string if hyperlinks are not supported.
 */
fun String.asLink(url: String): String {
    if (!doesTerminalSupportHyperlinks()) return this
    val bell = '\u0007' // Bell character (acts as separator)
    return "$CONTROL_CODE]8;;$url$bell$this$CONTROL_CODE]8;;$bell"
}

/**
 * Gets the current terminal width by executing `tput cols`, `stty size`, or checking COLUMNS, or using a fallback
 * value.
 *
 * @return The terminal width in columns.
 */
fun getTerminalWidth(): Int {
    try {
        val tputCols = runBlocking { runCommand(command = "tput cols", workingDir = null, exitOnError = false) }
        if (tputCols.isSuccess) return tputCols.output.trim().toInt()

        val sttySize = runBlocking { runCommand(command = "stty size", workingDir = null, exitOnError = false) }
        if (sttySize.isSuccess) return sttySize.output.trim().split(" ").last().toInt()

        return System.getenv("COLUMNS")?.toIntOrNull() ?: 80
    } catch (_: Exception) {
        return 80
    }
}

val isVerbose = args.contains("--verbose") || args.contains("-v")

/**
 * Gets the value of a command-line argument by name or short name.
 *
 * @param name The full name of the argument (e.g., "verbose").
 * @param shortName The short name of the argument (e.g., "v").
 * @return The value of the argument if found, otherwise `null`.
 */
fun getArg(name: String, shortName: String? = null): String? {
    val nameFlag = "--$name"
    val shortNameFlag = shortName?.let { "-$it" }

    val values =
        args
            .asSequence()
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

/**
 * Runs a shell command and captures its output and exit code.
 *
 * @param command The command to run.
 * @param workingDir The working directory for the command. Can be `null` to use the current directory.
 * @param timeoutAmount The maximum duration to wait for the command to complete. Defaults to 60 seconds.
 * @param exitOnError If `true`, the process will exit if the command returns a non-zero exit code. Defaults to `true`.
 * @return A [CmdResult] object containing the output and exit code of the command.
 */
suspend fun runCommand(
    @Language("shell") command: String,
    workingDir: File?,
    timeoutAmount: Duration = 60.seconds,
    exitOnError: Boolean = true,
    outputRedirect: Redirect = Redirect.CAPTURE,
): CmdResult {
    val result =
        withTimeout(timeoutAmount) {
            process(
                command = command.split(" ").toTypedArray(),
                stdout = outputRedirect,
                stderr = outputRedirect,
                directory = workingDir,
            )
        }

    val output = result.output.joinToString("\n")
    return if (result.resultCode == 0) {
        CmdResult.Success(output)
    } else {
        if (exitOnError) {
            throw PrintMessage(
                buildString {
                    appendLine()
                    append("Command '$command' failed with exit code ${result.resultCode}".asError())
                    if (output.isNotBlank()) {
                        appendLine(":".asError())
                        appendLine(output.asError())
                    } else {
                        appendLine()
                    }
                },
                printError = true,
            )
        }
        CmdResult.Failure(output)
    }
}

// Similar to Kotlin's Result, but always carries output
sealed interface CmdResult {
    val output: String

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    /** Gets the output of the command if it was successful, otherwise throws an exception. */
    fun getOrThrow(): String =
        if (isSuccess) {
            output
        } else {
            throw UnsupportedOperationException("Command failed with output $output")
        }

    /**
     * Represents a successful command execution.
     *
     * @param output The standard output of the command.
     */
    data class Success(override val output: String) : CmdResult

    /**
     * Represents a failed command execution.
     *
     * @param output The standard error of the command.
     */
    data class Failure(override val output: String) : CmdResult
}

/**
 * Gets the date of the latest release from the "RELEASE NOTES.md" file.
 *
 * @return The latest release date in "yyyy-mm-dd" format if found, otherwise `null`.
 */
fun getLatestReleaseDate(baseDir: File = File(".").canonicalFile): String? {
    val releaseNotesFile = File(baseDir, "RELEASE NOTES.md")
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
    printlnWarn("⚠️ Could not find any release date in RELEASE NOTES.md.")
    return null
}

/**
 * Checks whether the given directory is (part of) a git repository.
 *
 * @return true if it is, false otherwise.
 */
suspend fun isDirectoryGitRepo(directory: File): Boolean =
    runCommand("git rev-parse --is-inside-work-tree", directory, exitOnError = false).isSuccess

/**
 * Checks whether the given branch exists in the current git repository.
 *
 * @param branch The name of the branch to check.
 * @return true if the branch exists, false otherwise.
 */
suspend fun branchExists(branch: String, directory: File): Boolean =
    runCommand("git rev-parse --verify $branch", directory).isSuccess

val isWindows = "windows" in System.getProperty("os.name").lowercase()

fun exitWithError(message: String): Nothing {
    throw PrintMessage(message.asError(), printError = true)
}

suspend fun getCurrentBranchName(directory: File): String =
    runCommand("git rev-parse --abbrev-ref HEAD", directory, exitOnError = true).output.trim()
