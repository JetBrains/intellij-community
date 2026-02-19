// Forked from https://github.com/autonomousapps/dependency-analysis-gradle-plugin
// Copyright (c) 2025. Tony Robalik.
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.jewel.buildlogic.metalava

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.jewel.buildlogic.metalava.GenerateMetalavaApiTask.Companion.EXPERIMENTAL_API_ARGS
import org.jetbrains.jewel.buildlogic.metalava.GenerateMetalavaApiTask.Companion.STABLE_ONLY_API_ARGS

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

    @TaskAction
    fun action() {
        logger.lifecycle(
            "Checking current APIs from ${currentApiFile.get().asFile.canonicalPath} " +
                "against ${referenceApiFile.get().asFile.canonicalPath}"
        )
        workerExecutor.classLoaderIsolation().submit(Action::class.java) {
            projectPath.set(this@CheckMetalavaApiTask.projectPath)
            metalavaClasspath.setFrom(this@CheckMetalavaApiTask.metalavaClasspath)
            jdkHome.set(this@CheckMetalavaApiTask.jdkHome)
            referenceApiFile.set(this@CheckMetalavaApiTask.referenceApiFile)
            currentApiFile.set(this@CheckMetalavaApiTask.currentApiFile)
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
        val stableApiOnly: Property<Boolean>
        val updateBaseline: Property<Boolean>
        val baselineFile: ConfigurableFileCollection
    }

    abstract class Action : WorkAction<Parameters> {
        @get:Inject abstract val execOps: ExecOperations

        override fun execute() {
            val jdkHome = parameters.jdkHome.get()

            val result =
                execOps.javaexec {
                    systemProperty("java.awt.headless", "true")
                    systemProperty("apple.awt.UIElement", "true")
                    mainClass.set("com.android.tools.metalava.Driver")
                    classpath = parameters.metalavaClasspath
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
                val msg =
                    """
                    [Metalava] The API _might_ have changed in an incompatible way!
                    The Metalava process exited with a non-zero value — check the stderr output for details. 

                    If this is intended, and this is not happening during the release process,
                    update the baseline file and then commit the changes.

                    IMPORTANT: DO NOT USE JDK 24 WHEN RUNNING METALAVA.
                    """
                        .trimIndent()
                        .wrapInStars(margin = 1)
                
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
    }
}
