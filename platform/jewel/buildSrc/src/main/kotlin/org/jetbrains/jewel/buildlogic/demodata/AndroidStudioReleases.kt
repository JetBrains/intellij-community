package org.jetbrains.jewel.buildlogic.demodata

import com.squareup.kotlinpoet.ClassName
import gradle.kotlin.dsl.accessors._327d2b3378ed6d2c1bec5d20438f90c7.kotlin
import gradle.kotlin.dsl.accessors._327d2b3378ed6d2c1bec5d20438f90c7.sourceSets
import java.io.File
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty

open class StudioVersionsGenerationExtension(project: Project) {

    val targetDir: DirectoryProperty =
        project.objects
            .directoryProperty()
            .convention(
                project.layout.dir(project.provider { project.sourceSets.named("main").get().kotlin.srcDirs.first() })
            )

    val resourcesDirs: SetProperty<File> =
        project.objects
            .setProperty<File>()
            .convention(
                project.provider {
                    when {
                        project.plugins.hasPlugin("org.gradle.jvm-ecosystem") ->
                            project.extensions.getByType<SourceSetContainer>()["main"].resources.srcDirs

                        else -> emptySet()
                    }
                }
            )

    val dataUrl: Property<String> =
        project.objects.property<String>().convention("https://jb.gg/android-studio-releases-list.json")
}

internal const val STUDIO_RELEASES_OUTPUT_CLASS_NAME =
    "org.jetbrains.jewel.samples.ideplugin.releasessample.AndroidStudioReleases"

open class AndroidStudioReleasesGeneratorTask : DefaultTask() {

    @get:OutputFile val outputFile: RegularFileProperty = project.objects.fileProperty()

    @get:Input val dataUrl = project.objects.property<String>()

    @get:Input val resourcesDirs = project.objects.setProperty<File>()

    init {
        group = "jewel"
    }

    @TaskAction
    fun generate() {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val url = dataUrl.get()
        val lookupDirs = resourcesDirs.get()

        logger.lifecycle("Fetching Android Studio releases list from $url...")
        logger.debug("Registered resources directories:\n" + lookupDirs.joinToString("\n") { " * ${it.absolutePath}" })
        val releases = URI.create(url).toURL().openStream().use { json.decodeFromStream<ApiAndroidStudioReleases>(it) }

        val className = ClassName.bestGuess(STUDIO_RELEASES_OUTPUT_CLASS_NAME)
        val file = AndroidStudioReleasesReader.readFrom(releases, className, url, lookupDirs)

        val outputFile = outputFile.get().asFile
        outputFile.bufferedWriter().use { file.writeTo(it) }
        logger.lifecycle("Android Studio releases from $url parsed and code generated into ${outputFile.path}")
    }
}
