package org.jetbrains.jewel.buildlogic.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.archivesName
import org.jmailen.gradle.kotlinter.KotlinterExtension
import org.jmailen.gradle.kotlinter.tasks.LintTask

@Suppress("unused") // Plugin entry point, see build.gradle.kts
class KtlintConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jmailen.kotlinter")
            val extension = extensions.getByType<KotlinterExtension>()
            configureExtension(extension)
            configureTasks()
        }
    }

    private fun configureExtension(extension: KotlinterExtension) {
        extension.apply {
            ignoreFailures = false
            reporters = arrayOf("plain", "html", "sarif")
        }
    }

    private fun Project.configureTasks() {
        tasks.withType<LintTask>().configureEach {
            reports.set(
                mapOf(
                    "plain" to rootDir.resolve("build/reports/ktlint-${project.archivesName}.txt"),
                    "html" to rootDir.resolve("build/reports/ktlint-${project.archivesName}.html"),
                    "sarif" to rootDir.resolve("build/reports/ktlint-${project.archivesName}.sarif")
                )
            )
        }
    }
}
