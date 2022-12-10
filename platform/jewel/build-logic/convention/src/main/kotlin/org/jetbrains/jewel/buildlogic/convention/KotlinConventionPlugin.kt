package org.jetbrains.jewel.buildlogic.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinConventionPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            val extension = extensions.getByType<KotlinJvmProjectExtension>()
            configureExtension(extension)
        }
    }

    private fun Project.configureExtension(extension: KotlinJvmProjectExtension) {
        with (extension) {
            target {
                compilations.all {
                    kotlinOptions {
                        jvmTarget = "17"
                        freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
                    }
                }
            }
        }
    }
}
