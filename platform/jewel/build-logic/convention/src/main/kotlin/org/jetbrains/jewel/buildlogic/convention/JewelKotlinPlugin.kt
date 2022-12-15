package org.jetbrains.jewel.buildlogic.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

@Suppress("unused") // Plugin entry point, see build.gradle.kts
class JewelKotlinPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            val extension = extensions.getByType<KotlinJvmProjectExtension>()
            configureExtension(extension)

            // TODO move to a better place
            group = "org.jetbrains.jewel"
            version = "0.0.1-SNAPSHOT"
        }
    }

    private fun configureExtension(extension: KotlinJvmProjectExtension) {
        extension.apply {
            target {
                compilations.all {
                    kotlinOptions {
                        jvmTarget = "17"
                    }
                }
            }

            sourceSets.all {
                languageSettings {
                    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                    optIn("kotlin.experimental.ExperimentalTypeInference")
                    optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                }
            }
        }
    }
}
