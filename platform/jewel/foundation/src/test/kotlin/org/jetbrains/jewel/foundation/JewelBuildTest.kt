// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
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

    @Test
    fun `KOTLIN_VERSION in the jewel-checks workflow should match the build Kotlin in libs versions toml`() {
        val jewelHome = findJewelHomeDir()

        val libsVersionsToml = jewelHome.resolve("gradle/libs.versions.toml")
        if (!libsVersionsToml.isFile) {
            error("Cannot load the libs.versions.toml file from ${libsVersionsToml.absolutePath}")
        }

        // The workflow lives at the repository root, outside the Jewel tree, so it is not present in
        // every environment that runs these tests (e.g. the Bazel sandbox). Skip when it is missing.
        val workflowFile = jewelHome.parentFile?.parentFile?.resolve(".github/workflows/jewel-checks.yml")
        assumeTrue(
            "Skipping: the jewel-checks workflow is not available in this environment " +
                "(${workflowFile?.absolutePath}).",
            workflowFile?.isFile == true,
        )

        // libs.versions.toml is the source of truth; the workflow must follow it.
        val buildKotlin = readBuildKotlinVersion(libsVersionsToml)
        check(buildKotlin.isNotBlank()) {
            "Could not find a `kotlin = \"...\"` entry under [versions] in ${libsVersionsToml.absolutePath}"
        }

        val ciKotlin = readWorkflowKotlinVersion(workflowFile!!)
        check(ciKotlin.isNotBlank()) { "Could not find a `KOTLIN_VERSION:` env entry in ${workflowFile.absolutePath}" }

        val errorMessage = buildString {
            appendLine("The Kotlin version is out of sync between the Jewel build and the Jewel Checks CI workflow!")
            appendLine("Build Kotlin (source of truth): '$buildKotlin'")
            appendLine("Workflow KOTLIN_VERSION:        '$ciKotlin'")
            appendLine("----------------------------------------------------------")
            appendLine("To fix, set KOTLIN_VERSION in '.github/workflows/jewel-checks.yml' to '$buildKotlin'")
            appendLine("so it matches the 'kotlin' version in 'platform/jewel/gradle/libs.versions.toml'.")
        }

        assertEquals(errorMessage, buildKotlin, ciKotlin)
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

    // Matches the `kotlin = "<version>"` entry under [versions] (not kotlinpoet, kotlinx*, etc.).
    private fun readBuildKotlinVersion(libsVersionsToml: File): String =
        Regex("""(?m)^\s*kotlin\s*=\s*"([^"]+)"""").find(libsVersionsToml.readText())?.groupValues?.get(1).orEmpty()

    // Matches the `KOTLIN_VERSION: <version>` workflow env entry (with or without surrounding quotes).
    private fun readWorkflowKotlinVersion(workflowFile: File): String =
        Regex("""(?m)^\s*KOTLIN_VERSION:\s*"?([^"\s#]+)"?""")
            .find(workflowFile.readText())
            ?.groupValues
            ?.get(1)
            .orEmpty()
}
