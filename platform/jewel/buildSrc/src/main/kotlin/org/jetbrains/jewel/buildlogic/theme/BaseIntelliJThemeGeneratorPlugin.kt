package org.jetbrains.jewel.buildlogic.theme

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property
import java.io.File

abstract class BaseIntelliJThemeGeneratorPlugin : Plugin<Project> {

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

    protected abstract fun createExtension(project: Project): ThemeGeneratorContainer

    protected abstract fun createGenerateTask(project: Project, extension: ThemeGeneratorContainer): List<TaskProvider<out BaseThemeGeneratorTask>>
}

class ThemeGeneratorContainer(container: NamedDomainObjectContainer<ThemeGeneration>) : NamedDomainObjectContainer<ThemeGeneration> by container

class ThemeGeneration(val name: String, private val project: Project) {

    val targetDir: DirectoryProperty = project.objects.directoryProperty()
    val ideaVersion = project.objects.property<String>()
    val themeClassName = project.objects.property<String>()
    val themeFile = project.objects.property<String>()

    fun targetDir(): String = targetDir.get().asFile.absolutePath

    fun targetDir(path: File) {
        targetDir.set(path)
    }

    fun targetDir(path: Directory) {
        targetDir.set(path)
    }

    fun ideaVersion(): String = ideaVersion.get()

    fun ideaVersion(version: String) {
        ideaVersion.set(version)
    }

    fun themeClassName(): String = themeClassName.get()

    fun themeClassName(name: String) {
        themeClassName.set(name)
    }

    fun themeFile(): String = themeFile.get()

    fun themeFile(path: String) {
        themeFile.set(path)
    }
}
