// Forked from https://github.com/autonomousapps/dependency-analysis-gradle-plugin
// Copyright (c) 2025. Tony Robalik.
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.jewel.buildlogic.metalava

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

@CacheableTask
abstract class GenerateMetalavaApiTask @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {
    init {
        group = MetalavaConfigurer.TASK_GROUP
    }

    @get:Classpath abstract val metalavaClasspath: ConfigurableFileCollection

    @get:Classpath abstract val compileClasspath: ConfigurableFileCollection

    @get:Input abstract val jdkHome: Property<String>

    @get:Input abstract val stableApiOnly: Property<Boolean>

    @get:PathSensitive(PathSensitivity.RELATIVE) @get:InputFiles abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputFile abstract val output: RegularFileProperty

    @TaskAction
    fun action() {
        workerExecutor.classLoaderIsolation().submit(Action::class.java) {
            metalavaClasspath.setFrom(this@GenerateMetalavaApiTask.metalavaClasspath)
            compileClasspath.setFrom(this@GenerateMetalavaApiTask.compileClasspath)
            sourceFiles.setFrom(this@GenerateMetalavaApiTask.sourceFiles)
            jdkHome.set(this@GenerateMetalavaApiTask.jdkHome)
            output.set(this@GenerateMetalavaApiTask.output)
            stableApiOnly.set(this@GenerateMetalavaApiTask.stableApiOnly)
        }
    }

    interface Parameters : WorkParameters {
        val metalavaClasspath: ConfigurableFileCollection
        val compileClasspath: ConfigurableFileCollection
        val jdkHome: Property<String>
        val sourceFiles: ConfigurableFileCollection
        val output: RegularFileProperty
        val stableApiOnly: Property<Boolean>
    }

    abstract class Action : WorkAction<Parameters> {
        @get:Inject abstract val execOps: ExecOperations

        override fun execute() {
            val output = parameters.output.get().asFile

            // A `:`-delimited list of directories containing source files, organized in a standard Java package
            // hierarchy.
            val sourcePath = parameters.sourceFiles.files.filter(File::exists).joinToString(":") { it.absolutePath }

            val classpathString =
                parameters.compileClasspath.files
                    .asSequence()
                    .filter(File::exists)
                    .map { it.absolutePath }
                    .joinToString(":")

            val jdkHome = parameters.jdkHome.get()

            execOps.javaexec {
                systemProperty("java.awt.headless", "true")
                systemProperty("apple.awt.UIElement", "true")
                mainClass.set("com.android.tools.metalava.Driver")
                classpath = parameters.metalavaClasspath
                args =
                    listOf(
                        "--format=v4",
                        "--jdk-home",
                        jdkHome,
                        "--classpath",
                        classpathString,
                        "--source-path",
                        sourcePath,
                        "--api",
                        output.absolutePath,
                        "--warnings-as-errors",
                    ) +
                        if (parameters.stableApiOnly.get()) {
                            STABLE_ONLY_API_ARGS
                        } else {
                            EXPERIMENTAL_API_ARGS
                        }
            }
        }
    }

    companion object {
        private val BASE_API_ARGS =
            listOf(
                "--hide-annotation",
                "org.jetbrains.jewel.foundation.InternalJewelApi",
                "--hide-annotation",
                "org.jetbrains.annotations.ApiStatus.Internal",
                "--suppress-compatibility-meta-annotation",
                "org.jetbrains.jewel.foundation.GeneratedFromIntelliJSources",
            )

        internal val EXPERIMENTAL_API_ARGS =
            BASE_API_ARGS +
                listOf(
                    "--suppress-compatibility-meta-annotation",
                    "org.jetbrains.jewel.foundation.ExperimentalJewelApi",
                    "--suppress-compatibility-meta-annotation",
                    "org.jetbrains.annotations.ApiStatus.Experimental",
                )

        internal val STABLE_ONLY_API_ARGS =
            EXPERIMENTAL_API_ARGS +
                listOf(
                    "--hide-annotation",
                    "org.jetbrains.jewel.foundation.ExperimentalJewelApi",
                    "--hide-annotation",
                    "org.jetbrains.annotations.ApiStatus.Experimental",
                )
    }
}
