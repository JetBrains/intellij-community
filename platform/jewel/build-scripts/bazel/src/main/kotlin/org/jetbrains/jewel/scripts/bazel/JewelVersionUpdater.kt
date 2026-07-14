package org.jetbrains.jewel.scripts.bazel

import java.io.File
import java.util.Properties
import kotlin.system.exitProcess

// Run it like: bazel run //platform/jewel/build-scripts/bazel:jewelVersionUpdater
fun main(args: Array<String>) {
    print("⏳ Locating Jewel folder...")

    val jewelDir = getJewelRoot()

    if (jewelDir == null || !jewelDir.isDirectory) {
        printlnErr(
            "Could not find the Jewel folder. Please make sure you're running the script from somewhere inside it."
        )
        exitProcess(1)
    }

    println(" DONE: ${jewelDir.absolutePath}")

    print("🔍 Looking up Jewel API version...")

    val apiVersion = loadJewelVersionFromProperties(jewelDir)

    println(" DONE: $apiVersion")

    print("✍️ Writing updated Jewel API version...")

    val outFile = updateJewelApiVersion(jewelDir, apiVersion)

    print(" DONE\n    Generated ")

    val relativePath = outFile.relativeTo(getBuildWorkspaceDirectory()).path
    println(relativePath.asLink("file://${outFile.absolutePath}"))
}

// =============================== HELPERS =============================== //

private fun loadJewelVersionFromProperties(jewelDir: File): String {
    val propertiesFile = File(jewelDir, "gradle.properties")

    if (!propertiesFile.isFile) {
        printlnErr("Could not find the gradle.properties file.")
        exitProcess(1)
    }

    val properties = Properties().apply { propertiesFile.inputStream().use { load(it) } }
    val version = properties.getProperty("jewel.release.version")?.trim()

    if (version.isNullOrBlank()) {
        printlnErr("Could not find the Jewel API version in gradle.properties (jewel.release.version)")
        exitProcess(1)
    }
    return version
}

private fun updateJewelApiVersion(jewelDir: File, apiVersion: String): File {
    val outFile =
        File(jewelDir, "foundation/src/main/generated-kotlin/org/jetbrains/jewel/foundation/JewelApiVersion.kt")

    outFile.parentFile.mkdirs()

    outFile.writeText(jewelApiVersionTemplate.replace(JEWEL_API_VERSION_PLACEHOLDER, apiVersion))

    return outFile
}

private const val JEWEL_API_VERSION_PLACEHOLDER = "%%JEWEL_VERSION%%"

private val jewelApiVersionTemplate =
    """
    |// ATTENTION: this file is auto-generated. DO NOT EDIT MANUALLY!
    |// Use the jewel-version-updater script instead.
    |
    |package org.jetbrains.jewel.foundation
    |
    |/** The Jewel API version for this build, expressed as a string. E.g.: "0.30.0" */
    |public val JewelBuild.apiVersionString: String
    |    get() = "%%JEWEL_VERSION%%"
    |
    """
        .trimMargin()
