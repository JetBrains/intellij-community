package org.jetbrains.jewel.scripts.bazel

import com.github.ajalt.clikt.core.PrintMessage

fun exitWithError(message: String): Nothing {
    throw PrintMessage(message.asError(), statusCode = 1, printError = true)
}
