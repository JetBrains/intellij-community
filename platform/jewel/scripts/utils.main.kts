@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
@file:DependsOn("com.github.pgreze:kotlin-process:1.5.1")
@file:Suppress("RAW_RUN_BLOCKING")

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.intellij.lang.annotations.Language

fun checkGhTool() = runBlocking { runCommand(command = "which gh", workingDir = null, exitOnError = false).isSuccess }

fun checkPrNumber() = System.getenv("PR_NUMBER")?.trim()?.toIntOrNull() != null

fun getPrNumber() = checkNotNull(System.getenv("PR_NUMBER")?.trim()) { "PR number not set" }

fun requireGhTool() {
    if (checkGhTool()) return

    printlnErr("ERROR: the GitHub CLI tool must be present on the PATH.")
    exitProcess(1)
}

fun findJewelRoot(base: File = File("").canonicalFile): File? {
    fun isJewelDir(file: File): Boolean = file.name == "jewel" && file.parentFile.name == "platform"

    var file = base
    while (file.isDirectory) {
        if (isJewelDir(file)) return file.canonicalFile
        if (file.parentFile == null) break
        file = file.parentFile
    }
    return null
}

fun requirePrNumber() {
    if (checkPrNumber()) return

    printlnErr("ERROR: PR_NUMBER environment variable not set.")
    exitProcess(2)
}

private val CODE_ERR = "\u001b[0;31m"
private val CODE_WARN = "\u001b[0;33m"
private val CODE_CLEAR = "\u001b[0m"

fun printlnErr(message: String) {
    System.err.println(message.asError())
}

fun printlnWarn(message: String) {
    System.err.println(message.asWarning())
}

fun String.asWarning() = "$CODE_WARN$this$CODE_CLEAR"

fun String.asError() = "$CODE_ERR$this$CODE_CLEAR"

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

fun String.asLink(url: String): String {
    if (!doesTerminalSupportHyperlinks()) return this

    val esc = '\u001B' // Escape character
    val bell = '\u0007' // Bell character (acts as separator)
    return "$esc]8;;$url$bell$this$esc]8;;$bell"
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

suspend fun runCommand(
    @Language("shell") command: String,
    workingDir: File?,
    timeoutAmount: Duration = 60.seconds,
    exitOnError: Boolean = true,
): CmdResult {
    val result =
        withTimeout(timeoutAmount) {
            process(
                command = command.split(" ").toTypedArray(),
                stdout = Redirect.CAPTURE,
                stderr = Redirect.CAPTURE,
                directory = workingDir,
            )
        }

    val output = result.output.joinToString("\n")
    return if (result.resultCode == 0) {
        CmdResult.Success(output)
    } else {
        if (exitOnError) {
            printlnErr("Command '$command' failed with exit code ${result.resultCode}:\n$output")
            exitProcess(result.resultCode)
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

    fun getOrThrow(): String =
        if (isSuccess) {
            output
        } else {
            throw UnsupportedOperationException("Command failed with output $output")
        }

    data class Success(override val output: String) : CmdResult

    data class Failure(override val output: String) : CmdResult
}
