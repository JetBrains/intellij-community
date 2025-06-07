package org.jetbrains.jewel.buildlogic.theme

import com.squareup.kotlinpoet.ClassName
import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property

class ThemeGeneratorContainer(container: NamedDomainObjectContainer<ThemeGeneration>) :
    NamedDomainObjectContainer<ThemeGeneration> by container

class ThemeGeneration(val name: String, project: Project) {

    val targetDir: DirectoryProperty =
        project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("generated/theme"))
    val ideaVersion = project.objects.property<String>()
    val themeClassName = project.objects.property<String>()
    val themeFile = project.objects.property<String>()
}

open class IntelliJThemeGeneratorTask : DefaultTask() {

    @get:OutputFile val outputFile: RegularFileProperty = project.objects.fileProperty()

    @get:Input val ideaVersion = project.objects.property<String>()

    @get:Input val themeFile = project.objects.property<String>()

    @get:Input val themeClassName = project.objects.property<String>()

    init {
        group = "jewel"
    }

    @TaskAction
    fun generate() {
        val json = Json { ignoreUnknownKeys = true }
        val url = buildString {
            append("https://raw.githubusercontent.com/JetBrains/intellij-community/")
            append(ideaVersion.get())
            append("/")
            append(themeFile.get())
        }

        logger.lifecycle("Fetching theme descriptor from $url...")
        val themeDescriptor =
            URI.create(url).toURL().openStream().use { json.decodeFromStream<IntellijThemeDescriptor>(it) }

        val className = ClassName.bestGuess(themeClassName.get())
        val file = IntUiThemeDescriptorReader.readThemeFrom(themeDescriptor, className, ideaVersion.get(), url)

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
