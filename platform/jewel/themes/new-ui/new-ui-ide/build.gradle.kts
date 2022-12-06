import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.compose.jetbrainsCompose
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.archivesName

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.detekt)
}

detekt {
    config = files(File(rootDir, "detekt.yml"))
    buildUponDefaultConfig = true
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlin.experimental.ExperimentalTypeInference")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
            }
        }
    }
}

repositories {
    maven("https://androidx.dev/storage/compose-compiler/repository/")
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    mavenCentral()
}

dependencies {
    api(projects.themes.newUi.newUiStandalone)
    compileOnly(libs.bundles.idea)
}

tasks.named<Detekt>("detekt").configure {
    reports {
        sarif.required.set(true)
        sarif.outputLocation.set(file(File(rootDir, "build/reports/detekt-${project.archivesName}.sarif")))
    }
}
