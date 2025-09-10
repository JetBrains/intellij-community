// Forked from https://github.com/autonomousapps/dependency-analysis-gradle-plugin
// Copyright (c) 2025. Tony Robalik.
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.jewel.buildlogic.metalava

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class UpdateMetalavaApiTask : DefaultTask() {
    init {
        group = MetalavaConfigurer.TASK_GROUP
    }

    @get:PathSensitive(PathSensitivity.RELATIVE) @get:InputFile abstract val input: RegularFileProperty

    @get:OutputFile abstract val output: RegularFileProperty

    @TaskAction
    fun action() {
        logger.lifecycle("Copying Metalava API report from ${input.get()} to ${output.get()}")
        input.get().asFile.copyTo(output.get().asFile, overwrite = true)
    }
}
