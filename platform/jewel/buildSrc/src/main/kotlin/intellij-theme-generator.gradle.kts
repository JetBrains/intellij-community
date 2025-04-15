@file:Suppress("UnstableApiUsage")

import com.squareup.kotlinpoet.ClassName
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.util.internal.GUtil
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.tasks.DokkaGenerateModuleTask
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import org.jetbrains.jewel.buildlogic.theme.IntelliJThemeGeneratorTask
import org.jetbrains.jewel.buildlogic.theme.ThemeGeneration
import org.jetbrains.jewel.buildlogic.theme.ThemeGeneratorContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

val extension = ThemeGeneratorContainer(container<ThemeGeneration> { ThemeGeneration(it, project) })

extensions.add("intelliJThemeGenerator", extension)

extension.all {
    val task =
        tasks.register<IntelliJThemeGeneratorTask>("generate${GUtil.toCamelCase(name)}Theme") {
            val paths =
                this@all.themeClassName.map {
                    val className = ClassName.bestGuess(it)
                    className.packageName.replace(".", "/") + "/${className.simpleName}.kt"
                }

            outputFile = targetDir.file(paths)
            themeClassName = this@all.themeClassName
            ideaVersion = this@all.ideaVersion
            themeFile = this@all.themeFile
        }

    tasks {
        withType<BaseKotlinCompile> { dependsOn(task) }
        withType<Detekt> { dependsOn(task) }
        withType<DokkaTask> { dependsOn(task) }
        withType<DokkaGenerateModuleTask> { dependsOn(task) }
        withType<DokkaGeneratePublicationTask> { dependsOn(task) }
        withType<Jar> { dependsOn(task) }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        the<KotlinJvmProjectExtension>().sourceSets["main"].kotlin.srcDir(targetDir)
    }
}
