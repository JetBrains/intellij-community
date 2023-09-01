@file:Suppress("UnstableApiUsage")

import io.gitlab.arturbosch.detekt.Detekt
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("org.jmailen.kotlinter")
    kotlin("jvm")
}

group = "org.jetbrains.jewel"

val gitHubRef: String? = System.getenv("GITHUB_REF")
version = when {
    gitHubRef?.startsWith("refs/tags/") == true -> {
        gitHubRef.substringAfter("refs/tags/")
            .removePrefix("v")
    }

    else -> "1.0.0-SNAPSHOT"
}

kotlin {
    jvmToolchain(17)
    target {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs += "-Xcontext-receivers"
            }
        }
        sourceSets.all {
            languageSettings {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlin.experimental.ExperimentalTypeInference")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("androidx.compose.foundation.ExperimentalFoundationApi")
                optIn("org.jetbrains.jewel.ExperimentalJewelApi")
            }
        }
    }
}

detekt {
    config = files(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
}

val sarif by configurations.creating {
    isCanBeConsumed = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("sarif"))
    }
}

tasks {
    withType<Detekt> {
        val sarifOutputFile = layout.buildDirectory.file("reports/detekt-${project.name}.sarif")
        exclude { it.file.absolutePath.startsWith(layout.buildDirectory.asFile.get().absolutePath) }
        reports {
            sarif.required = true
            sarif.outputLocation = sarifOutputFile
        }
        sarif.outgoing {
            artifact(sarifOutputFile) {
                builtBy(this@withType)
            }
        }
    }
    withType<LintTask> {
        exclude { it.file.absolutePath.startsWith(layout.buildDirectory.asFile.get().absolutePath) }
        val sarifReport = layout.buildDirectory.file("reports/ktlint-${project.name}.sarif")
        reports = provider {
            mapOf(
                "plain" to layout.buildDirectory.file("reports/ktlint-${project.name}.txt").get().asFile,
                "html" to layout.buildDirectory.file("reports/ktlint-${project.name}.html").get().asFile,
                "sarif" to sarifReport.get().asFile
            )
        }

        sarif.outgoing {
            artifact(sarifReport) {
                builtBy(this@withType)
            }
        }
    }
}
