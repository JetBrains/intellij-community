@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
@file:DependsOn("com.github.pgreze:kotlin-process:1.5.1")
@file:Suppress("RAW_RUN_BLOCKING")

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.intellij.lang.annotations.Language
import java.io.File

fun checkGhTool() = runBlocking { runCommand(command = "which gh", workingDir = null, exitOnError = false).isSuccess }

fun checkPrNumber() = System.getenv("PR_NUMBER")?.trim()?.toIntOrNull() != null

fun getPrNumber() = checkNotNull(System.getenv("PR_NUMBER")?.trim()) { "PR number not set" }

fun requireGhTool() {
    if (checkGhTool()) return

    echoErr("ERROR: the GitHub CLI tool must be present on the PATH.")
    exitProcess(1)
}

fun requirePrNumber() {
    if (checkPrNumber()) return

    echoErr("ERROR: PR_NUMBER environment variable not set.")
    exitProcess(2)
}

fun echoErr(message: String) {
    System.err.println("\u001b[0;31m$message\u001b[0m")
}

fun echoWarn(message: String) {
    System.err.println("\u001b[0;33m$message\u001b[0m")
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
            echoErr("Command '$command' failed with exit code ${result.resultCode}:\n$output")
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
