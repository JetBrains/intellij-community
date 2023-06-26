import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("org.jmailen.kotlinter")
    kotlin("jvm")
}

group = "org.jetbrains.jewel"

val GITHUB_REF: String? = System.getenv("GITHUB_REF")

version = when {
    GITHUB_REF?.startsWith("refs/tags/") == true -> GITHUB_REF.substringAfter("refs/tags/")
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
            }
        }
    }
}

detekt {
    config = files(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
}

tasks {
    register<MergeSarifTask>("mergeSarifReports") {
        dependsOn(check)
        source = rootProject.fileTree("build/reports") {
            include("*.sarif")
            exclude("static-analysis.sarif")
        }
        outputs.file(rootProject.file("build/reports/static-analysis.sarif"))
    }
    withType<Detekt> {
        reports {
            sarif.required.set(true)
            sarif.outputLocation.set(file(rootDir.resolve("build/reports/detekt-${project.name}.sarif")))
        }
    }
}
