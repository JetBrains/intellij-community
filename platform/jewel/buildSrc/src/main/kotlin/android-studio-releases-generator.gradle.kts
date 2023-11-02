@file:Suppress("UnstableApiUsage")

import com.squareup.kotlinpoet.ClassName
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.jewel.buildlogic.demodata.AndroidStudioReleasesGeneratorTask
import org.jetbrains.jewel.buildlogic.demodata.STUDIO_RELEASES_OUTPUT_CLASS_NAME
import org.jetbrains.jewel.buildlogic.demodata.StudioVersionsGenerationExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

val extension: StudioVersionsGenerationExtension =
    extensions.findByType<StudioVersionsGenerationExtension>()
        ?: extensions.create("androidStudioReleasesGenerator", StudioVersionsGenerationExtension::class.java)

val task =
    tasks.register<AndroidStudioReleasesGeneratorTask>("generateAndroidStudioReleasesList") {
        val className = ClassName.bestGuess(STUDIO_RELEASES_OUTPUT_CLASS_NAME)
        val filePath = className.packageName.replace(".", "/") +
            "/${className.simpleName}.kt"
        outputFile = extension.targetDir.file(filePath)
        dataUrl = extension.dataUrl
        resourcesDirs = extension.resourcesDirs
    }

tasks {
    withType<BaseKotlinCompile> { dependsOn(task) }
    withType<Detekt> { dependsOn(task) }
}

pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    the<KotlinJvmProjectExtension>().sourceSets["main"].kotlin.srcDir(extension.targetDir)
}
