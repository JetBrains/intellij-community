package org.jetbrains.jewel.buildlogic.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

@Suppress("unused") // Plugin entry point, see build.gradle.kts
class JewelKotlinPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            val extension = extensions.getByType<KotlinJvmProjectExtension>()
            configureExtension(extension)
            extensions.getByType<JavaPluginExtension>().apply {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(17))
                }
            }

            // TODO move to a better place
            group = "org.jetbrains.jewel"
            version = "0.0.1-SNAPSHOT"

            tasks.register<Jar>("sourcesJar") {
                from(extension.sourceSets["main"].kotlin)
                archiveClassifier.set("source")
            }
        }
    }

    private fun configureExtension(extension: KotlinJvmProjectExtension) {
        extension.apply {
            target {
                compilations.all {
                    kotlinOptions {
                        jvmTarget = "17"
                        freeCompilerArgs += "-Xcontext-receivers"
                    }
                }
            }

            sourceSets.all {
                languageSettings {
                    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                    optIn("kotlin.experimental.ExperimentalTypeInference")
                    optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                    optIn("androidx.compose.foundation.ExperimentalFoundationApi")
                }
            }
        }
    }
}
