// Forked from https://github.com/autonomousapps/dependency-analysis-gradle-plugin
// Copyright (c) 2025. Tony Robalik.
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.jewel.buildlogic.metalava

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

/**
 * Examples of invocations include:
 * 1. `./gradlew :metalavaHelp`
 * 1. `./gradlew :metalavaHelp --args="main --help"`
 * 1. `./gradlew :metalavaHelp --args="help issues"`
 */
@UntrackedTask(because = "Not worth tracking")
abstract class MetalavaHelperTask @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {
    init {
        group = MetalavaConfigurer.TASK_GROUP
        description = "Runs metalava with arbitrary arguments."
    }

    @get:Classpath abstract val metalavaClasspath: ConfigurableFileCollection

    @get:Option(option = "args", description = "Metalava args")
    @get:Optional
    @get:Input
    abstract val args: Property<String>

    @TaskAction
    fun action() {
        workerExecutor.classLoaderIsolation().submit(
            Action::class.java,
        ) {
            metalavaClasspath.setFrom(this@MetalavaHelperTask.metalavaClasspath)
            args.set(this@MetalavaHelperTask.args)
        }
    }

    interface Parameters : WorkParameters {
        val metalavaClasspath: ConfigurableFileCollection
        val args: Property<String>
    }

    abstract class Action : WorkAction<Parameters> {
        private val logger = Logging.getLogger(MetalavaHelperTask::class.java)

        @get:Inject abstract val execOps: ExecOperations

        override fun execute() {
            val argsList = parameters.args.orNull?.split(' ')?.map { it.trim() } ?: listOf("help")
            logger.lifecycle("Running metalava with args '$argsList'")

            execOps.javaexec {
                mainClass.set("com.android.tools.metalava.Driver")
                classpath = parameters.metalavaClasspath
                args = argsList
            }
        }
    }
}
