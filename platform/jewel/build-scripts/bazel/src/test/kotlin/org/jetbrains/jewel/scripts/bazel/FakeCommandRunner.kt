package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.time.Duration

class FakeCommandRunner(private val handler: (command: String) -> CmdResult = { CmdResult.Success("") }) :
    CommandRunner {
    val calls = mutableListOf<String>()

    override suspend fun invoke(
        command: String,
        workingDir: File?,
        timeoutAmount: Duration,
        exitOnError: Boolean,
        streamOutput: Boolean,
    ): CmdResult {
        calls.add(command)

        val result = handler(command)
        if (result.isFailure && exitOnError) {
            error(
                buildString {
                    appendLine()
                    append("Command '$command' failed")
                    if (result.output.isNotBlank()) {
                        appendLine(":")
                        appendLine(result.output)
                    } else {
                        appendLine()
                    }
                }
            )
        }
        return result
    }
}
