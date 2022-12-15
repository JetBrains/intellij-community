package org.jetbrains.jewel.buildlogic.convention

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

@Suppress("unused") // Plugin entry point, see build.gradle.kts
class JewelDetektPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            val extension = extensions.getByType<DetektExtension>()
            configureExtension(extension)
            configureTasks()
        }
    }

    private fun Project.configureExtension(extension: DetektExtension) {
        extension.apply {
            config = files(rootProject.file("detekt.yml"))
            buildUponDefaultConfig = true
        }
    }

    private fun Project.configureTasks() {
        tasks.withType<Detekt>().configureEach {
            reports {
                sarif.required.set(true)
                sarif.outputLocation.set(file(rootDir.resolve("build/reports/detekt-${project.name}.sarif")))
            }
        }
    }
}
