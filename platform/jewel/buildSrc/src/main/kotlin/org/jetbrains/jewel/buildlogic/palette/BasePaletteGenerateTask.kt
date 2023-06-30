package org.jetbrains.jewel.buildlogic.palette

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property

abstract class BasePaletteGenerateTask : DefaultTask() {

    @get:OutputFile
    val output = project.objects.fileProperty()

    @get:Input
    val ideaVersion = project.objects.property<String>()

    @get:Input
    val themeFile = project.objects.property<String>()

    @get:Input
    val paletteClassName = project.objects.property<String>()

    init {
        group = "jewel"
    }

    @TaskAction
    fun generate() {
        doGenerate()
    }

    protected abstract fun doGenerate()
}
