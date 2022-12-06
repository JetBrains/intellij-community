import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.archivesName

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.ideaGradlePlugin)
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
                freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn", "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
            }
        }
    }
    sourceSets {
        all {
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.optIn("kotlin.experimental.ExperimentalTypeInference")
            languageSettings.optIn("androidx.compose.ui.ExperimentalComposeUiApi")
        }
    }
}

intellij {
    pluginName.set("Jewel")
    version.set("LATEST-EAP-SNAPSHOT")
    plugins.set(listOf("org.jetbrains.kotlin"))
    version.set("2022.3") // IJ 22.3 RC2
}

repositories {
    maven("https://androidx.dev/storage/compose-compiler/repository/")
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    mavenCentral()
}

dependencies {
    implementation(projects.themes.darcula.darculaIde)
}

tasks.named<Detekt>("detekt").configure {
    reports {
        sarif.required.set(true)
        sarif.outputLocation.set(file(File(rootDir, "build/reports/detekt-${project.archivesName}.sarif")))
    }
}
