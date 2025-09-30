#!/usr/bin/env kotlin
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Import("utils.main.kts")

import kotlin.system.exitProcess
import java.io.File
import java.util.Properties

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

    outFile.writeText(jewelApiVersionTemplate.replace(jewelApiVersionPlaceholder, apiVersion))

    return outFile
}

private val jewelApiVersionPlaceholder = "%%JEWEL_VERSION%%"

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

// =============================== ENTRY POINT =============================== //

print("⏳ Locating Jewel folder...")

private val jewelDir = findJewelRoot()

if (jewelDir == null || !jewelDir.isDirectory) {
    printlnErr("Could not find the Jewel folder. Please make sure you're running the script from somewhere inside it.")
    exitProcess(1)
}

println(" DONE: ${jewelDir!!.absolutePath}")

print("🔍 Looking up Jewel API version...")

val apiVersion = loadJewelVersionFromProperties(jewelDir!!)

println(" DONE: $apiVersion")

print("✍️ Writing updated Jewel API version...")

val outFile = updateJewelApiVersion(jewelDir!!, apiVersion)

print(" DONE\n    Generated ")

println(outFile.toRelativeString(jewelDir!!).asLink("file://${outFile.absolutePath}"))
