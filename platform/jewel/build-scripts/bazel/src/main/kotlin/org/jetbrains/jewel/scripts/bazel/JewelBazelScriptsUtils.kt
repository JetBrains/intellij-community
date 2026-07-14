package org.jetbrains.jewel.scripts.bazel

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language

private const val CONTROL_CODE = '\u001b'
private const val CODE_SUCCESS = "$CONTROL_CODE[0;32m"
private const val CODE_ERR = "$CONTROL_CODE[0;31m"
private const val CODE_WARN = "$CONTROL_CODE[0;33m"
private const val CODE_BOLD = "$CONTROL_CODE[1m"
private const val CODE_CLEAR = "$CONTROL_CODE[0m"

/**
 * Gets the argument at [index], or throws if it's missing.
 *
 * @param index The index of the argument to retrieve.
 * @return The argument at [index].
 */
fun Array<String>.getOrThrow(index: Int) = this.getOrNull(index) ?: error("Index $index does not have any arguments")

/**
 * Gets the Bazel workspace root directory.
 *
 * @return The workspace directory, read from the `BUILD_WORKSPACE_DIRECTORY` environment variable. Throws if that
 *   variable isn't set or doesn't point at a valid directory — this only works when run via `bazel run`.
 */
fun getBuildWorkspaceDirectory(): File =
    File(System.getenv("BUILD_WORKSPACE_DIRECTORY")).takeIf { it.isDirectory }
        ?: error("BUILD_WORKSPACE_DIRECTORY is not set or is not a valid directory. Run via `bazel run`.".asError())

/**
 * Gets the Jewel subproject's root directory.
 *
 * @return The `platform/jewel` directory under the Bazel workspace root, or `null` if it doesn't exist.
 */
fun getJewelRoot(): File? = File(getBuildWorkspaceDirectory(), "platform/jewel").takeIf { it.isDirectory }

/**
 * Gets the runfiles directory for the current `bazel run` invocation.
 *
 * @return The runfiles directory path, read from the `RUNFILES_DIR` or `JAVA_RUNFILES` environment variable. Throws if
 *   neither is set — this only works when run via `bazel run`.
 */
fun getRunfilesDir() =
    System.getenv("RUNFILES_DIR")
        ?: System.getenv("JAVA_RUNFILES")
        ?: error("RUNFILES_DIR is not set. Run via `bazel run`.")

val isWindows = "windows" in System.getProperty("os.name").lowercase()

private val stylingSupported = doesTerminalSupportStyling()

/**
 * Detects if the current terminal supports styling (colors, bold, etc.).
 *
 * @return `true` if a compatible terminal is detected, `false` otherwise.
 */
private fun doesTerminalSupportStyling(): Boolean =
    System.getenv("TERM")?.contains("color") == true || System.getenv("COLORTERM") == "truecolor"

fun red(text: String) = "\u001B[31m$text\u001B[0m"

/**
 * Formats a string as a success (green) text with ANSI escape codes if the terminal supports it.
 *
 * @return The formatted text, or the text itself if the terminal does not support styling.
 */
fun String.asSuccess(): String = if (stylingSupported) "$CODE_SUCCESS$this$CODE_CLEAR" else this

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
 * Prints a success message to the standard output stream with success (green) formatting.
 *
 * @param message The success message to print.
 */
fun printlnSuccess(message: String) {
    println(message.asSuccess())
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
 * Prints an error message to the standard error stream with error formatting.
 *
 * @param message The error message to print.
 */
fun printlnErr(message: String) {
    System.err.println(message.asError())
}

/**
 * Checks whether the given directory is (part of) a git repository.
 *
 * @return true if it is, false otherwise.
 */
suspend fun isDirectoryGitRepo(directory: File, runner: CommandRunner = DefaultCommandRunner): Boolean =
    runner("git rev-parse --is-inside-work-tree", directory, exitOnError = false).isSuccess

/**
 * Checks whether the given branch exists in the current git repository.
 *
 * @param branch The name of the branch to check.
 * @return true if the branch exists, false otherwise.
 */
suspend fun branchExists(branch: String, directory: File, runner: CommandRunner = DefaultCommandRunner): Boolean =
    runner("git rev-parse --verify $branch", directory).isSuccess

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

interface CommandRunner {
    suspend operator fun invoke(
        @Language("shell") command: String,
        workingDir: File?,
        timeoutAmount: Duration = 60.seconds,
        exitOnError: Boolean = true,
        streamOutput: Boolean = false,
    ): CmdResult
}

object DefaultCommandRunner : CommandRunner {
    override suspend fun invoke(
        command: String,
        workingDir: File?,
        timeoutAmount: Duration,
        exitOnError: Boolean,
        streamOutput: Boolean,
    ): CmdResult = runCommand(command, workingDir, timeoutAmount, exitOnError, streamOutput)
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
private suspend fun runCommand(
    @Language("shell") command: String,
    workingDir: File?,
    timeoutAmount: Duration = 60.seconds,
    exitOnError: Boolean = true,
    streamOutput: Boolean = false,
): CmdResult =
    withContext(Dispatchers.IO) {
        val process =
            ProcessBuilder(command.split(" "))
                .directory(workingDir)
                .redirectErrorStream(true)
                .redirectOutput(if (streamOutput) ProcessBuilder.Redirect.INHERIT else ProcessBuilder.Redirect.PIPE)
                .redirectError(if (streamOutput) ProcessBuilder.Redirect.INHERIT else ProcessBuilder.Redirect.PIPE)
                .start()

        val outputFuture =
            if (!streamOutput) {
                CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
            } else null
        val exitCode = process.waitFor()
        val output = outputFuture?.get(timeoutAmount.inWholeSeconds, TimeUnit.SECONDS) ?: ""

        if (exitCode == 0) {
            CmdResult.Success(output)
        } else {
            if (exitOnError) {
                error(
                    buildString {
                        appendLine()
                        append("Command '$command' failed with exit code $exitCode".asError())
                        if (output.isNotBlank()) {
                            appendLine(":".asError())
                            appendLine(output.asError())
                        } else {
                            appendLine()
                        }
                    }
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
            output.trim()
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

suspend fun getCurrentBranchName(directory: File, runner: CommandRunner = DefaultCommandRunner): String =
    runner("git rev-parse --abbrev-ref HEAD", directory, exitOnError = true).output.trim()

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
