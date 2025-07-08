package org.jetbrains.jewel.buildlogic.theme

import com.squareup.kotlinpoet.ClassName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property

class ThemeGeneratorContainer(container: NamedDomainObjectContainer<ThemeGeneration>) :
    NamedDomainObjectContainer<ThemeGeneration> by container

class ThemeGeneration(val name: String, project: Project) {

    val targetDir: DirectoryProperty =
        project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("generated/theme"))
    val themeClassName = project.objects.property<String>()
    val themeFilePath = project.objects.property<String>()
}

@CacheableTask
abstract class IntelliJThemeGeneratorTask : DefaultTask() {

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val themeFile: RegularFileProperty

    @get:Input
    abstract val themeFilePath: Property<String>

    @get:Input abstract val themeClassName: Property<String>

    init {
        group = "jewel"
    }

    @TaskAction
    fun generate() {
        val json = Json { ignoreUnknownKeys = true }

        val themeFile = themeFile.get().asFile

        logger.lifecycle("Fetching theme descriptor from ${themeFile.name}...")
        val themeDescriptor = themeFile.inputStream().use { json.decodeFromStream<IntellijThemeDescriptor>(it) }

        val className = ClassName.bestGuess(themeClassName.get())
        val file = IntUiThemeDescriptorReader.readThemeFrom(themeDescriptor, className, themeFilePath.get())

        val outputFile = outputFile.get().asFile
        logger.lifecycle(
            "Theme descriptor for ${themeDescriptor.name} parsed and " + "code generated into ${outputFile.path}"
        )
        outputFile.bufferedWriter().use { file.writeTo(it) }
    }
}

@Serializable
data class IntellijThemeDescriptor(
    val name: String,
    val author: String = "",
    val dark: Boolean = false,
    val editorScheme: String,
    val colors: Map<String, String> = emptyMap(),
    val ui: Map<String, JsonElement> = emptyMap(),
    val icons: Map<String, JsonElement> = emptyMap(),
    val iconColorsOnSelection: Map<String, Int> = emptyMap(),
)
