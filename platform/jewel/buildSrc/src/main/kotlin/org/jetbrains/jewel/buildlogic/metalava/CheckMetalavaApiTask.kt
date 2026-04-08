// Forked from https://github.com/autonomousapps/dependency-analysis-gradle-plugin
// Copyright (c) 2025. Tony Robalik.
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.jewel.buildlogic.metalava

import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.jewel.buildlogic.metalava.GenerateMetalavaApiTask.Companion.EXPERIMENTAL_API_ARGS
import org.jetbrains.jewel.buildlogic.metalava.GenerateMetalavaApiTask.Companion.STABLE_ONLY_API_ARGS
import org.jetbrains.jewel.buildlogic.metalava.apiDiffer.MetalavaApiDumpDiffer
import org.jetbrains.jewel.buildlogic.metalava.apiDiffer.getDiffTextOrNull
import org.jetbrains.jewel.buildlogic.metalava.patcher.PatchWriter

@CacheableTask
abstract class CheckMetalavaApiTask @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {
    init {
        group = MetalavaConfigurer.TASK_GROUP
    }

    @get:Input abstract val projectPath: Property<String>

    @get:Classpath abstract val metalavaClasspath: ConfigurableFileCollection

    @get:Input abstract val jdkHome: Property<String>

    @get:Input abstract val stableApiOnly: Property<Boolean>

    @get:Input abstract val updateBaseline: Property<Boolean>

    @get:PathSensitive(PathSensitivity.RELATIVE) @get:InputFile abstract val referenceApiFile: RegularFileProperty

    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val baselineFile: ConfigurableFileCollection

    @get:PathSensitive(PathSensitivity.RELATIVE) @get:InputFile abstract val currentApiFile: RegularFileProperty

    @get:OutputFile abstract val patchFile: RegularFileProperty

    @TaskAction
    fun action() {
        logger.lifecycle(
            "Checking current APIs from ${currentApiFile.get().asFile.canonicalPath} " +
                "against ${referenceApiFile.get().asFile.canonicalPath}"
        )
        workerExecutor.classLoaderIsolation().submit(RunMetalavaAction::class.java) {
            projectPath.set(this@CheckMetalavaApiTask.projectPath)
            metalavaClasspath.setFrom(this@CheckMetalavaApiTask.metalavaClasspath)
            jdkHome.set(this@CheckMetalavaApiTask.jdkHome)
            referenceApiFile.set(this@CheckMetalavaApiTask.referenceApiFile)
            currentApiFile.set(this@CheckMetalavaApiTask.currentApiFile)
            patchFile.set(this@CheckMetalavaApiTask.patchFile)
            stableApiOnly.set(this@CheckMetalavaApiTask.stableApiOnly)
            updateBaseline.set(this@CheckMetalavaApiTask.updateBaseline)
            baselineFile.from(this@CheckMetalavaApiTask.baselineFile)
        }
    }

    interface Parameters : WorkParameters {
        val projectPath: Property<String>
        val metalavaClasspath: ConfigurableFileCollection
        val jdkHome: Property<String>
        val referenceApiFile: RegularFileProperty
        val currentApiFile: RegularFileProperty
        val patchFile: RegularFileProperty
        val stableApiOnly: Property<Boolean>
        val updateBaseline: Property<Boolean>
        val baselineFile: ConfigurableFileCollection
    }

    abstract class RunMetalavaAction : WorkAction<Parameters> {
        @get:Inject abstract val execOps: ExecOperations

        override fun execute() {
            val jdkHome = parameters.jdkHome.get()
            // Remove old API dump diff patch file if exists
            parameters.patchFile.get().asFile.delete()

            val stderr = ByteArrayOutputStream()

            val result =
                execOps.javaexec {
                    systemProperty("java.awt.headless", "true")
                    systemProperty("apple.awt.UIElement", "true")
                    mainClass.set("com.android.tools.metalava.Driver")
                    classpath = parameters.metalavaClasspath
                    errorOutput = stderr
                    args = buildList {
                        add("--format=v4")
                        add("--jdk-home")
                        add(jdkHome)
                        add("--source-files")
                        add(parameters.currentApiFile.get().asFile.absolutePath)
                        add("--check-compatibility:api:released")
                        add(parameters.referenceApiFile.get().asFile.absolutePath)
                        add("--warnings-as-errors")

                        if (parameters.stableApiOnly.get()) {
                            addAll(STABLE_ONLY_API_ARGS)
                        } else {
                            addAll(EXPERIMENTAL_API_ARGS)
                        }

                        val baselineFile = parameters.baselineFile.singleFile
                        baselineFile.createNewFile() // Ensure baseline file exists

                        add("--baseline")
                        add(baselineFile.absolutePath)

                        if (parameters.updateBaseline.get()) {
                            add("--pass-baseline-updates")
                            add("--update-baseline")
                            add(baselineFile.absolutePath)
                        }
                    }
                    isIgnoreExitValue = true
                }

            if (result.exitValue != 0) {
                val metalavaOutput = stderr.toString(Charsets.UTF_8).trim()
                val refFile = parameters.referenceApiFile.get().asFile
                val curFile = parameters.currentApiFile.get().asFile

                val apiDiffer = MetalavaApiDumpDiffer(
                    refMetalavaApiDump = refFile,
                    currentMetalavaApiDump = curFile,
                ) { diffCommandContext: MetalavaApiDumpDiffer.DiffCommandContext ->
                    val execResult = execOps.exec {
                        isIgnoreExitValue = diffCommandContext.ignoreExitValue
                        standardOutput = diffCommandContext.stdOutput
                        errorOutput = diffCommandContext.errorOutput
                        commandLine(diffCommandContext.arguments)
                    }
                    MetalavaApiDumpDiffer.DiffExecResult(execResult.exitValue)
                }
                val patchResult = apiDiffer.generateDiffPatch()
                val writeResult = patchResult.getDiffTextOrNull()?.let {
                    PatchWriter(parameters.patchFile.get().asFile)
                        .write(it) 
                }
                
                val msg = buildString {
                    val apiSurface = if (parameters.stableApiOnly.get()) "Stable" else "Experimental"

                    appendLine(
                        """
                    [Metalava] $apiSurface API compatibility check failed.

                    If this API change is intended, apply the generated patch to update the API dump file and commit it.

                    If this failure should be suppressed instead (for example, a hidden/internal API issue),
                    update the Metalava baseline file accordingly and commit that change.

                    See docs/api-compatibility.md for compatibility and remediation guidance.
                    See docs/pr-guide.md and docs/releasing-guide.md for workflow details.

                    IMPORTANT: DO NOT USE JDK 24 WHEN RUNNING METALAVA.
                    """.trimIndent().wrapInStars(margin = 1)
                    )
                    appendMetalavaAPIDumpPatchContent(apiSurface, patchResult)
                    appendLine()
                    appendPatchFileLocation(apiSurface, writeResult)
                }
                
                throw ApiChangedException(msg)
            }
        }

        private fun String.wrapInStars(margin: Int = 0): String {
            require(margin >= 0) { "Expected margin >= 0, was $margin" }

            fun StringBuilder.insertVerticalMargin() {
                repeat(margin) { appendLine() }
            }

            fun StringBuilder.insertLeftMargin() {
                append(" ".repeat(margin))
            }

            fun StringBuilder.insertRightMargin() {
                appendLine(" ".repeat(margin))
            }

            val lines = lines()
            return buildString {
                // Add top margin
                insertVerticalMargin()

                // Get length of longest line
                val max = lines.maxBy { it.length }.length

                // Top line
                insertLeftMargin()
                append("*".repeat(max + 4))
                insertRightMargin()

                lines.forEach { line ->
                    insertLeftMargin()
                    append("* ")
                    append(line)

                    val padding = max - line.length
                    append(" ".repeat(padding))
                    append(" *")
                    insertRightMargin()
                }

                // Bottom line
                insertLeftMargin()
                append("*".repeat(max + 4))
                appendLine(" ".repeat(margin))

                // Add bottom margin
                insertVerticalMargin()
            }
        }
        
        private fun StringBuilder.appendMetalavaAPIDumpPatchContent(
            apiSurface: String,
            patchResult: MetalavaApiDumpDiffer.PatchGenerationResult,
        ) {
            when (patchResult) {
               is MetalavaApiDumpDiffer.PatchGenerationResult.Generated -> {
                   wrapWithPatchMarkers(apiSurface, patchResult.diffText)
               }
               MetalavaApiDumpDiffer.PatchGenerationResult.NoChanges -> {
                   appendLine("$apiSurface API dump patch not generated because there were no changes.")
               }
               MetalavaApiDumpDiffer.PatchGenerationResult.DiffUnavailable -> {
                   appendLine("$apiSurface API dump patch could not be produced because the 'diff' executable is not available.")
               }
               is MetalavaApiDumpDiffer.PatchGenerationResult.DiffFailed -> {
                   appendLine("$apiSurface API dump patch could not be produced because running 'diff' failed.")
                   appendLine(patchResult.message)
               }
            }
        }
        
        private fun StringBuilder.appendPatchFileLocation(
            apiSurface: String,
            patchWriteResult: PatchWriter.PatchWriteResult?,
        ) {
            when (patchWriteResult) {
                is PatchWriter.PatchWriteResult.Success -> {
                    val patchFilePath = patchWriteResult.patchFile.absolutePath
                    appendLine("$apiSurface API dump patch written to file: $patchFilePath")
                    appendLine("Apply it by running: git apply $patchFilePath")
                }
                is PatchWriter.PatchWriteResult.Failure -> {
                    appendLine("Unable to write $apiSurface API dump patch to file.")
                    appendLine(patchWriteResult.message)
                }
                null -> Unit
            }
        }
        
        private fun StringBuilder.wrapWithPatchMarkers(apiSurface: String, patchText: String) {
            appendLine("Generated $apiSurface API dump patch:")
            appendLine("----- BEGIN METALAVA API PATCH -----")
            append(patchText)
            appendLine("----- END METALAVA API PATCH -----")
        }
    }
}
