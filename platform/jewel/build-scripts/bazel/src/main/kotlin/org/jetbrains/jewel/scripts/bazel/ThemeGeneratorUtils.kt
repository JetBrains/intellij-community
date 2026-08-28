@file:Suppress("IO_FILE_USAGE")

package org.jetbrains.jewel.scripts.bazel

import java.io.File

/**
 * Reads and validates the IJP major build number from `build.txt` at the root of [communityRoot].
 *
 * @return The major build number component (e.g. `"253"` from `"253.1234.567"`).
 */
fun readIjpMajor(communityRoot: File): String {
    val buildNumberFile = File(communityRoot, "build.txt")
    check(buildNumberFile.isFile) { "The build.txt file must exist in the community root" }

    val buildNumber = buildNumberFile.readText().trim()
    check(validateBuildNumber(buildNumber)) { "The build number in build.txt does not seem valid: '$buildNumber'" }

    return buildNumber.substringBefore('.')
}

/**
 * Validates a build number string. Examples:
 * - `253.1234.567`
 * - `241.SNAPSHOT`
 * - `253.28294.SNAPSHOT`
 */
internal fun validateBuildNumber(buildNumber: String): Boolean {
    if (buildNumber.isBlank()) return false
    if (buildNumber.length < 5) return false
    if (buildNumber.take(3).toIntOrNull()?.takeIf { it > 240 } == null) return false
    if (buildNumber[3] != '.') return false

    val afterDot = buildNumber.drop(4)
    return afterDot == "SNAPSHOT" ||
        afterDot.all { it.isDigit() || it == '.' } ||
        (afterDot.endsWith(".SNAPSHOT") && afterDot.removeSuffix(".SNAPSHOT").all { it.isDigit() || it == '.' })
}
