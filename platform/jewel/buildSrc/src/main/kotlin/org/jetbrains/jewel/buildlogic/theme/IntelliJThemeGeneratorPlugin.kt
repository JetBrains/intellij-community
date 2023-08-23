package org.jetbrains.jewel.buildlogic.theme

import com.squareup.kotlinpoet.ClassName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.internal.GUtil
import java.net.URL

class IntelliJThemeGeneratorPlugin : BaseIntelliJThemeGeneratorPlugin() {

    override fun createExtension(project: Project): ThemeGeneratorContainer =
        ThemeGeneratorContainer(project.container(ThemeGeneration::class.java) {
            ThemeGeneration(it, project).apply {
                targetDir.set(project.layout.buildDirectory.dir("generated/theme"))
                ideaVersion.set("232.6734")
            }
        }).apply {
            project.extensions.add("intelliJThemeGenerator", this)
        }

    override fun createGenerateTask(
        project: Project,
        extension: ThemeGeneratorContainer
    ): List<TaskProvider<out BaseThemeGeneratorTask>> =
        extension.map { config ->
            project.tasks.register(
                "generate${GUtil.toCamelCase(config.name)}Theme",
                IntelliJThemeGeneratorTask::class.java
            ).apply {
                configure {
                    output.set(config.targetDir.file(config.themeClassName.map {
                        val className = ClassName.bestGuess(it)
                        className.packageName.replace('.', '/') + "/${className.simpleName}.kt"
                    }))
                    themeClassName.set(config.themeClassName)
                    ideaVersion.set(config.ideaVersion)
                    themeFile.set(config.themeFile)
                }
            }
        }
}

open class IntelliJThemeGeneratorTask : BaseThemeGeneratorTask() {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun doGenerate() {
        val url = buildString {
            append("https://raw.githubusercontent.com/JetBrains/intellij-community/")
            append(ideaVersion.get())
            append("/")
            append(themeFile.get())
        }

        logger.lifecycle("Fetching theme descriptor from $url...")

        val themeDescriptor = URL(url).openStream().use {
            json.decodeFromString<IntellijThemeDescriptor>(it.reader().readText())
        }

        logger.info("Theme descriptor fetched, parsing...")
        val className = ClassName.bestGuess(themeClassName.get())

        // TODO handle non-Int UI themes, too
        val file = IntUiThemeDescriptorReader.readThemeFrom(themeDescriptor, className, ideaVersion.get(), url)

        logger.info("Theme descriptor parsed, writing generated code to disk...")
        val outputFile = output.get().asFile
        outputFile.bufferedWriter().use {
            file.writeTo(it)
        }

        logger.lifecycle("Theme descriptor for ${themeDescriptor.name} parsed and code generated into ${outputFile.path}")
    }
}
