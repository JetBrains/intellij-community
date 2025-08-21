#!/usr/bin/env kotlin
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
@file:Import("utils.main.kts")
@file:Suppress("RAW_RUN_BLOCKING")

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes

private fun printHelp() {
    println(
        """
            Usage: kotlin metalava-signatures.main.kts <command> [options]
            Commands:
              update     Generate and update API signature dumps.
              validate   Validate the current signatures against previously generated ones.

            Options:
              --release <version>   Tells Metalava to create a versioned release archival dump (or validate against one).
                                    If omitted, it will use the current Jewel API version from gradle.properties.                                    
              --module <path>       Only checks/generates the dump for one module (e.g., ':ui').
              --stable-only         Only run tasks for the stable API surface.
              --experimental-only   Only run tasks for the experimental API surface.
              --force               Forces a clean build before running the tasks.
              --help                Display this help and exit.
        """
            .trimIndent()
    )
}

print("⏳ Locating Jewel folder...")

private val jewelDir = findJewelRoot()

if (jewelDir == null || !jewelDir.isDirectory) {
    printlnErr("Could not find the Jewel folder. Please make sure you're running the script from somewhere inside it.")
    exitProcess(1)
}

println(" DONE: ${jewelDir!!.absolutePath}")

if (args.isEmpty()) {
    printHelp()
    exitProcess(1)
}

private val verb = args.firstOrNull()
private val remainingArgs = args.drop(1)

private val baseTask =
    when (verb) {
        "update" -> "updateMetalava"
        "validate" -> "checkMetalava"
        "--help" -> {
            printHelp()
            exitProcess(0)
        }
        else -> {
            println("Unknown command: $verb")
            printHelp()
            exitProcess(1)
        }
    }

private var taskPath = ""
private var releaseFlag: String? = null
private var cleanTask: String? = null
private var apiSurface: String? = null

private val argIterator = remainingArgs.iterator()

while (argIterator.hasNext()) {
    when (val arg = argIterator.next()) {
        "--release" -> {
            if (!argIterator.hasNext()) {
                printlnErr("Error: Missing version for --release")
                printHelp()
                exitProcess(1)
            }
            val version = argIterator.next()
            if (version.startsWith("--")) {
                printlnErr("Error: Missing version for --release")
                printHelp()
                exitProcess(1)
            }
            releaseFlag = "-PmetalavaTargetRelease=$version"
        }
        "--module" -> {
            if (!argIterator.hasNext()) {
                printlnErr("Error: Missing module path for --module")
                printHelp()
                exitProcess(1)
            }
            val modulePath = argIterator.next()
            if (modulePath.startsWith("--")) {
                printlnErr("Error: Missing module path for --module")
                printHelp()
                exitProcess(1)
            }
            taskPath = "$modulePath:"
        }
        "--stable-only" -> {
            if (apiSurface != null) {
                printlnErr("Error: --stable-only and --experimental-only cannot be used at the same time.")
                printHelp()
                exitProcess(1)
            }
            apiSurface = "Stable"
        }
        "--experimental-only" -> {
            if (apiSurface != null) {
                printlnErr("Error: --stable-only and --experimental-only cannot be used at the same time.")
                printHelp()
                exitProcess(1)
            }
            apiSurface = "Experimental"
        }
        "--force" -> {
            cleanTask = "clean"
        }
        "--help" -> {
            printHelp()
            exitProcess(0)
        }
        else -> {
            println("Unknown option: $arg")
            printHelp()
            exitProcess(1)
        }
    }
}

private val apiSurfaceTaskNamePart = apiSurface ?: ""
private val task = "$baseTask${apiSurfaceTaskNamePart}Api"

private val commands = buildList {
    add("./gradlew")
    cleanTask?.let { add(it) }
    add("$taskPath$task")
    releaseFlag?.let { add(it) }
    add("--continue")
}

println("⏳ Executing: ${commands.joinToString(" ")}")

private val result = runBlocking {
    runCommand(commands.joinToString(" "), jewelDir, inheritIO = true, timeoutAmount = 60.minutes)
}

if (result.isSuccess) {
    println("\n✅ Done!")
}
