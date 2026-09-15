package org.jetbrains.jewel.scripts.bazel

import com.github.ajalt.clikt.core.PrintMessage

fun exitWithError(message: String): Nothing {
    throw PrintMessage(message.asError(), statusCode = 1, printError = true)
}

/**
 * Requires the GitHub CLI tool (`gh`) to be present on the system's PATH. Exits the process with an error code if the
 * tool is not found.
 */
suspend fun requireGhTool(commandRunner: CommandRunner) {
    if (checkGhTool(commandRunner)) return

    exitWithError("ERROR: the GitHub CLI tool must be present on the PATH.")
}

/**
 * Checks if the GitHub CLI tool (`gh`) is present on the system's PATH.
 *
 * @return `true` if the `gh` tool is found, `false` otherwise.
 */
suspend fun checkGhTool(commandRunner: CommandRunner): Boolean =
    commandRunner("which gh", null, exitOnError = false).isSuccess
