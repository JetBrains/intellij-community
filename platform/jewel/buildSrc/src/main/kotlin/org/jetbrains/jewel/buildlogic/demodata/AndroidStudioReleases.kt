package org.jetbrains.jewel.buildlogic.demodata

import com.squareup.kotlinpoet.ClassName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import java.io.File
import java.net.URI

open class StudioVersionsGenerationExtension(project: Project) {
    val targetDir: DirectoryProperty =
        project.objects
            .directoryProperty()
            .convention(
                project.layout.dir(
                    project.provider {
                        if (project.plugins.hasPlugin("org.gradle.jvm-ecosystem")) {
                            val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
                            val mainSourceSet = sourceSets.named("main").get()
                            val kotlinSourceSet = mainSourceSet.extensions.getByName("kotlin") as SourceDirectorySet
                            kotlinSourceSet.srcDirs.first()
                        } else {
                            error("Gradle plugin 'org.gradle.jvm-ecosystem' is required")
                        }
                    }
                )
            )

    val resourcesDirs: SetProperty<File> =
        project.objects
            .setProperty<File>()
            .convention(
                project.provider {
                    if (project.plugins.hasPlugin("org.gradle.jvm-ecosystem")) {
                        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
                        javaExtension.sourceSets.named("main").get().resources.srcDirs
                    } else {
                        emptySet<File>()
                    }
                }
            )

    val dataUrl: Property<String> =
        project.objects.property<String>().convention("https://jb.gg/android-studio-releases-list.json")

    /** Fully-qualified name of the generated object. Its package also drives the default output path. */
    val outputClassName: Property<String> =
        project.objects.property<String>().convention(STUDIO_RELEASES_OUTPUT_CLASS_NAME)

    /** Package that holds the `ContentItem` and `ContentSource` types the generated code refers to. */
    val modelPackage: Property<String> =
        project.objects.property<String>().convention(STUDIO_RELEASES_DEFAULT_MODEL_PACKAGE)

    /** Indentation unit used by the generated file. */
    val indent: Property<String> = project.objects.property<String>().convention("    ")
}

internal const val STUDIO_RELEASES_DEFAULT_MODEL_PACKAGE = "org.jetbrains.jewel.samples.ideplugin.releasessample"

internal const val STUDIO_RELEASES_OUTPUT_CLASS_NAME = "$STUDIO_RELEASES_DEFAULT_MODEL_PACKAGE.AndroidStudioReleases"

open class AndroidStudioReleasesGeneratorTask : DefaultTask() {
    @get:OutputFile val outputFile: RegularFileProperty = project.objects.fileProperty()

    @get:Input val dataUrl = project.objects.property<String>()

    @get:Input val resourcesDirs = project.objects.setProperty<File>()

    @get:Input val outputClassName = project.objects.property<String>()

    @get:Input val modelPackage = project.objects.property<String>()

    @get:Input val indent = project.objects.property<String>()

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

        val className = ClassName.bestGuess(outputClassName.get())
        val file =
            AndroidStudioReleasesReader.readFrom(
                releases = releases,
                className = className,
                url = url,
                resourceDirs = lookupDirs,
                modelPackage = modelPackage.get(),
                indentString = indent.get(),
            )

        val outputFile = outputFile.get().asFile
        outputFile.bufferedWriter().use { file.writeTo(it) }
        logger.lifecycle("Android Studio releases from $url parsed and code generated into ${outputFile.path}")
    }
}
