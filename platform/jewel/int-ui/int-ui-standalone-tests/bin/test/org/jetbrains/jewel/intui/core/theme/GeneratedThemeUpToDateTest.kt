// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.core.theme

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class GeneratedThemeUpToDateTest {
    @Test
    fun `should have the right major version in the light file header`() {
        val currentDir = File("").canonicalFile.takeIf { it.isDirectory } ?: error("Cannot find current directory")
        val majorBuildNumber = getMajorIjpVersion(currentDir)

        val themesDir = findThemesDir(currentDir)

        val themeFile = themesDir.resolve(LIGHT_THEME_FILENAME)
        assertTrue(themeFile.isFile, "The light theme file does not exist")

        val themeFirstLine = themeFile.useLines { lines -> lines.first() }
        assertTrue(
            themeFirstLine.endsWith(" for IJP $majorBuildNumber"),
            "The light theme file does not have the right IJP version in its header; " +
                "run the Jewel theme generator Gradle task to fix the issue.",
        )
    }

    @Test
    fun `should have the right major version in the dark file header`() {
        val currentDir = File("").canonicalFile.takeIf { it.isDirectory } ?: error("Cannot find current directory")
        val majorBuildNumber = getMajorIjpVersion(currentDir)

        val themesDir = findThemesDir(currentDir)

        val themeFile = themesDir.resolve(DARK_THEME_FILENAME)
        assertTrue(themeFile.isFile, "The dark theme file does not exist")

        val themeFirstLine = themeFile.useLines { lines -> lines.first() }
        assertTrue(
            themeFirstLine.endsWith(" for IJP $majorBuildNumber"),
            "The dark theme file does not have the right IJP version in its header; " +
                "run the Jewel theme generator Gradle task to fix the issue.",
        )
    }

    private fun getMajorIjpVersion(currentDir: File): String {
        val communityRoot = findCommunityRoot(currentDir) ?: error("Could not find the IJ community root directory")
        val buildNumber = communityRoot.resolve("build.txt").readText().trim()
        check(validateBuildNumber(buildNumber)) { "The build number in build.txt does not seem valid: '$buildNumber'" }

        return buildNumber.substringBefore(".")
    }

    private fun findCommunityRoot(base: File): File? {
        val firstAttempt =
            findDir(base) {
                it.resolve(COMMUNITY_ROOT_MARKER_FILE_NAME).isFile && it.resolve(COMMUNITY_IML_FILE_NAME).isFile
            }
        if (firstAttempt != null) return firstAttempt

        // We did not find it traversing up. Maybe we're in the monorepo, and it's actually a child?
        if (
            base.resolve(COMMUNITY_ROOT_MARKER_IN_MONOREPO_FILE_NAME).isFile &&
                base.resolve(COMMUNITY_IML_IN_MONOREPO_FILE_NAME).isFile
        ) {
            return base.resolve(COMMUNITY_DIR_IN_MONOREPO_NAME).takeIf { it.isDirectory }
        }

        // Could not find the community directory
        return null
    }

    private fun findDir(base: File, isDesiredDir: (File) -> Boolean): File? {
        var current = base
        while (true) {
            if (!current.canRead()) {
                System.err.println("Directory is not readable, stopping search: ${current.absolutePath}")
                return null
            }

            if (isDesiredDir(current)) {
                return current.canonicalFile
            }

            current = current.parentFile ?: return null
        }
    }

    private fun findThemesDir(currentDir: File): File {
        val resolved = currentDir.resolve(GENERATED_THEMES_DIR_PATH).absoluteFile
        val themesDir =
            resolved.takeIf { it.isDirectory } ?: error("Cannot find themes directory at ${resolved.canonicalPath}")
        assertEquals(2, themesDir.listFiles()?.size, "Number of generated theme files")
        return themesDir
    }

    private fun validateBuildNumber(buildNumber: String): Boolean {
        // Examples:
        //  * 253.1234.567
        //  * 241.SNAPSHOT
        //  * 253.28294.SNAPSHOT
        if (buildNumber.isBlank()) return false
        if (buildNumber.length < 5) return false
        if (buildNumber.take(3).toIntOrNull()?.takeIf { it > 240 } == null) return false
        if (buildNumber[3] != '.') return false

        val afterDot = buildNumber.drop(4)
        return afterDot == "SNAPSHOT" ||
            afterDot.all { it.isDigit() || it == '.' } ||
            (afterDot.endsWith(".SNAPSHOT") && afterDot.removeSuffix(".SNAPSHOT").all { it.isDigit() || it == '.' })
    }
}

private const val GENERATED_THEMES_DIR_PATH =
    "../int-ui-standalone/generated/theme/org/jetbrains/jewel/intui/core/theme/"
private const val DARK_THEME_FILENAME = "IntUiDarkTheme.kt"
private const val LIGHT_THEME_FILENAME = "IntUiLightTheme.kt"

// Values taken from JpsModuleToBazel's logic
private const val COMMUNITY_ROOT_MARKER_FILE_NAME = ".community.root.marker"
private const val COMMUNITY_IML_FILE_NAME = "intellij.idea.community.main.iml"
private const val COMMUNITY_DIR_IN_MONOREPO_NAME = "community"
private const val COMMUNITY_ROOT_MARKER_IN_MONOREPO_FILE_NAME =
    "$COMMUNITY_DIR_IN_MONOREPO_NAME/$COMMUNITY_ROOT_MARKER_FILE_NAME"
private const val COMMUNITY_IML_IN_MONOREPO_FILE_NAME = "$COMMUNITY_DIR_IN_MONOREPO_NAME/$COMMUNITY_IML_FILE_NAME"
