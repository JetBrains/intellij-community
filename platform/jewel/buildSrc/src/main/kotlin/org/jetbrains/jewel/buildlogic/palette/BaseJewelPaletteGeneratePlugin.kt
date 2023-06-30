package org.jetbrains.jewel.buildlogic.palette

import java.io.File
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property

abstract class BaseJewelPaletteGeneratePlugin : Plugin<Project> {

    final override fun apply(target: Project) {
        val extension = createExtension(target)

        target.afterEvaluate {
            val tasks = createGenerateTask(target, extension)
            val sourceSets = target.extensions.getByType<SourceSetContainer>()
            val mainSourceSet = sourceSets.getByName("main")

            mainSourceSet.java {
                extension.map { it.targetDir }.forEach {
                    srcDir(it)
                }
            }

            target.tasks.findByName("compileKotlin")?.dependsOn(*tasks.toTypedArray())
            target.tasks.findByName("lintKotlinMain")?.dependsOn(*tasks.toTypedArray())
        }
    }

    protected abstract fun createExtension(project: Project): PaletteGenerateContainer

    protected abstract fun createGenerateTask(project: Project, extension: PaletteGenerateContainer): List<TaskProvider<out BasePaletteGenerateTask>>
}

class PaletteGenerateContainer(container: NamedDomainObjectContainer<PaletteGenerate>) : NamedDomainObjectContainer<PaletteGenerate> by container

class PaletteGenerate(val name: String, project: Project) {

    val targetDir = project.objects.directoryProperty()

    val ideaVersion = project.objects.property<String>()

    val paletteClassName = project.objects.property<String>()

    val themeFile = project.objects.property<String>()

    fun targetDir(): String {
        return targetDir.get().asFile.absolutePath
    }

    fun targetDir(path: File) {
        targetDir.set(path)
    }

    fun targetDir(path: Directory) {
        targetDir.set(path)
    }

    fun ideaVersion(): String {
        return ideaVersion.get()
    }

    fun ideaVersion(version: String) {
        ideaVersion.set(version)
    }

    fun paletteClassName(): String {
        return paletteClassName.get()
    }

    fun paletteClassName(name: String) {
        paletteClassName.set(name)
    }

    fun themeFile(): String {
        return themeFile.get()
    }

    fun themeFile(path: String) {
        themeFile.set(path)
    }
}
