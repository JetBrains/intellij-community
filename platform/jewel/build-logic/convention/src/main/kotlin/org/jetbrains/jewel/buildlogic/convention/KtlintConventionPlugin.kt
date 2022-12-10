package org.jetbrains.jewel.buildlogic.convention

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.archivesName
import org.jmailen.gradle.kotlinter.KotlinterExtension
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask
import java.io.File

class KtlintConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jmailen.kotlinter")
            val extension = extensions.getByType<KotlinterExtension>()
            configureExtension(extension)
            configureTasks()
        }
    }

    private fun Project.configureExtension(extension: KotlinterExtension) {
        with(extension) {
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

        tasks.named("check") {
            dependsOn("installKotlinterPrePushHook")
        }
    }
}
