import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import dev.detekt.gradle.Detekt
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.tasks.DokkaGenerateModuleTask
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import org.jetbrains.jewel.buildlogic.metalava.GenerateMetalavaApiTask
import org.jetbrains.jewel.buildlogic.theme.DefaultColorPaletteGeneratorTask
import org.jetbrains.jewel.buildlogic.theme.PaletteSource
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

class ColorPaletteGenerator(val name: String, project: Project) {
    val themeFilePath = project.objects.property<String>()
    val themeFile: Provider<RegularFile> = project.layout.file(themeFilePath.map { project.rootProject.file(it) })
    val propertyName = project.objects.property<String>()
    val isIslands = project.objects.property<Boolean>()
}

val extension = container<ColorPaletteGenerator> { ColorPaletteGenerator(it, project) }

extensions.add("intelliJColorPaletteGenerator", extension)

val targetDir: Provider<Directory> = project.provider { project.layout.projectDirectory.dir("generated/theme") }

val generateDefaultColorPalette =
    tasks.register<DefaultColorPaletteGeneratorTask>("generateDefaultColorPalette") {
        sources.set(
            extension.map { entry ->
                project.objects.newInstance<PaletteSource>().apply {
                    themeFile.set(entry.themeFile)
                    propertyName.set(entry.propertyName)
                    islands.set(entry.isIslands)
                }
            }
        )
        outputFile = targetDir.map { it.file("org/jetbrains/jewel/ui/theme/DefaultColorPalette.kt") }
    }

tasks {
    withType<BaseKotlinCompile> { dependsOn(generateDefaultColorPalette) }
    withType<Detekt> { dependsOn(generateDefaultColorPalette) }
    withType<DokkaTask> { dependsOn(generateDefaultColorPalette) }
    withType<DokkaGenerateModuleTask> { dependsOn(generateDefaultColorPalette) }
    withType<DokkaGeneratePublicationTask> { dependsOn(generateDefaultColorPalette) }
    withType<Jar> { dependsOn(generateDefaultColorPalette) }
    withType<GenerateMetalavaApiTask> { dependsOn(generateDefaultColorPalette) }
    withType<KtfmtBaseTask> { dependsOn(generateDefaultColorPalette) }
}

pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    the<KotlinJvmProjectExtension>().sourceSets["main"].kotlin.srcDir(targetDir)
}
