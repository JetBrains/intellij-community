// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation

import java.io.File
import java.util.Properties
import org.junit.Test

private const val JEWEL_MARKER_FILE_NAME = "JEWEL_MARKER"

internal class JewelBuildTest {
    @Test
    fun `apiVersionString should have the same value as the one in the gradle properties`() {
        val jewelHome = findJewelHomeDir()
        val propertiesFile = jewelHome.resolve("gradle.properties")

        if (!propertiesFile.isFile) {
            error("Cannot load the gradle.properties file from ${propertiesFile.absolutePath}")
        }

        val expected = loadApiVersion(propertiesFile)
        if (expected.isBlank()) {
            error("The jewel.release.version value in the gradle.properties file must not be blank")
        }

        assert(JewelBuild.apiVersionString == expected) {
            "The version defined by the `jewel.release.version` value ($expected) in the gradle.properties file does " +
                "not match the one defined in `JewelBuild.apiVersionString` (${JewelBuild.apiVersionString}).\n\n" +
                "You can fix this by running the Jewel version updater script in the `jewel/scripts` folder."
        }
    }

    private fun findJewelHomeDir(): File {
        val initialFile = File(".").canonicalFile

        val ultimateJewel = initialFile.resolve("community/platform/jewel")
        if (ultimateJewel.isDirectory && ultimateJewel.resolve(JEWEL_MARKER_FILE_NAME).isFile) {
            println("Found Jewel folder at ${ultimateJewel.absolutePath} (ultimate checkout)")
            return ultimateJewel
        }

        val communityJewel = initialFile.resolve("platform/jewel")
        if (communityJewel.isDirectory && communityJewel.resolve(JEWEL_MARKER_FILE_NAME).isFile) {
            println("Found Jewel folder at ${communityJewel.absolutePath} (community checkout)")
            return communityJewel
        }

        var current = initialFile
        while (true) {
            if (!current.canRead()) {
                error("Directory is not readable, stopping search: ${current.absolutePath}")
            }

            val marker = current.resolve(JEWEL_MARKER_FILE_NAME)
            if (marker.isFile) {
                return current
            }

            current =
                current.parentFile
                    ?: error(
                        "Could not find a $JEWEL_MARKER_FILE_NAME file in any parent directory.\n" +
                            "Searched up from ${initialFile.absolutePath}"
                    )
        }
    }

    private fun loadApiVersion(file: File): String {
        val properties = Properties().apply { file.inputStream().use { load(it) } }
        return properties.getProperty("jewel.release.version").orEmpty()
    }
}
