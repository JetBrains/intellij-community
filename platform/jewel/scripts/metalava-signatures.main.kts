#!/usr/bin/env kotlin
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:5.0.3")
@file:Import("utils.main.kts")
@file:Suppress("RAW_RUN_BLOCKING")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.pgreze.process.Redirect
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking

private abstract class BaseMetalavaCommand(name: String) : CliktCommand(name = name) {
    private val release: String? by
        option(
            "--release",
            help =
                "Tells Metalava to check against, or create, a versioned release archival dump. " +
                    "If omitted, it will use the current Jewel API version from gradle.properties.",
        )
    private val module: String? by
        option("--module", help = "Only checks/generates the dump for one module (e.g., ':ui').")
    private val stableOnly by option("--stable-only", help = "Only run tasks for the stable API surface.").flag()
    private val experimentalOnly by
        option("--experimental-only", help = "Only run tasks for the experimental API surface.").flag()
    private val force by option("--force", help = "Forces a clean build before running the tasks.").flag()

    protected fun runTask(baseTask: String) {
        if (stableOnly && experimentalOnly) {
            printlnErr("Error: --stable-only and --experimental-only cannot be used at the same time.")
            exitProcess(1)
        }

        print("⏳ Locating Jewel folder...")
        val jewelDir = findJewelRoot()
        if (jewelDir == null || !jewelDir.isDirectory) {
            printlnErr(
                "Could not find the Jewel folder. " +
                    "Please make sure you're running the script from somewhere inside it."
            )
            exitProcess(1)
        }
        println(" DONE: ${jewelDir.absolutePath}")

        val apiSurface =
            when {
                stableOnly -> "Stable"
                experimentalOnly -> "Experimental"
                else -> ""
            }
        val task = "$baseTask${apiSurface}Api"

        val taskPath =
            module?.let {
                var path = it
                if (!path.startsWith(":")) {
                    path = ":$path"
                }
                "$path:"
            } ?: ""

        val releaseFlag = release?.let { "-PmetalavaTargetRelease=$it" }
        val cleanTask = if (force) "clean" else null

        val commands = buildList {
            add("./gradlew")
            cleanTask?.let { add(it) }
            add("$taskPath$task")
            releaseFlag?.let { add(it) }
            addAll(contributeArgs())
            add("--continue")
        }

        println("⏳ Executing: ${commands.joinToString(" ")}")

        val result = runBlocking {
            runCommand(
                commands.joinToString(" "),
                jewelDir,
                timeoutAmount = 60.minutes,
                outputRedirect = Redirect.PRINT,
            )
        }

        if (result.isSuccess) {
            println("\n✅ Done!")
        }
    }

    protected open fun contributeArgs(): List<String> = emptyList()
}

private class UpdateCommand : BaseMetalavaCommand(name = "update") {
    override fun help(context: Context): String = "Update stored Metalava API signature dumps."

    override fun run() {
        runTask("updateMetalava")
    }
}

private class ValidateCommand : BaseMetalavaCommand(name = "validate") {
    private val updateBaseline by
        option(
                "--update-baseline",
                "--update-baselines",
                help = "Writes any API check issues to the baseline file for this release.",
            )
            .flag()

    override fun help(context: Context): String = "Validate the current signatures against previously stored ones."

    override fun run() {
        runTask("checkMetalava")
    }

    override fun contributeArgs(): List<String> =
        if (updateBaseline) {
            listOf("-Pupdate-baseline=true")
        } else {
            emptyList()
        }
}

private class MetalavaSignaturesCommand : CliktCommand() {
    override fun help(context: Context): String =
        "A script to update and validate Metalava API signatures for Jewel modules."

    override fun run() = Unit
}

MetalavaSignaturesCommand().subcommands(UpdateCommand(), ValidateCommand()).main(args)
