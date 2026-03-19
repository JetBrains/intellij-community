package org.jetbrains.jewel.buildlogic.metalava

import java.io.File

/**
 * Resolves the IDEA project root from a file under the Jewel module. Walks up from [base] to
 * the first directory containing `.community.root.marker`; returns its parent instead if that
 * parent contains `.ultimate.root.marker`.
 *
 * @param base Starting directory for the upward search.
 * @return Project root directory, or `null` if no marker is found.
 */
internal fun guessIdeaProjectDir(base: File): File? {
    val communityRoot =
        findDir(base) { directory ->
            directory.resolve(".community.root.marker").isFile
        } ?: return null

    return when (val possibleUltimateRoot = communityRoot.parentFile) {
        null -> communityRoot
        else ->
            if (possibleUltimateRoot.resolve(".ultimate.root.marker").isFile) {
                possibleUltimateRoot
            } else {
                communityRoot
            }
    }
}

/**
 * Walks upward from [base] until [isDesiredDir] matches, or returns `null` at the filesystem root.
 *
 * @param base Starting directory.
 * @param isDesiredDir Predicate that identifies the target directory.
 */
private fun findDir(base: File, isDesiredDir: (File) -> Boolean): File? {
    var current = base.canonicalFile
    while (true) {
        if (isDesiredDir(current)) {
            return current
        }

        current = current.parentFile ?: return null
    }
}
