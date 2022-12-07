import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
                optIn("kotlin.time.ExperimentalTime")
                optIn("androidx.compose.foundation.ExperimentalFoundationApi")
            }
        }
    }
}

dependencies {
    implementation(projects.themes.newUi.newUiDesktop)
    implementation(libs.compose.components.splitpane)
}

compose.desktop {
    application {
        mainClass = "org.jetbrains.jewel.samples.standalone.expui.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Jewel New UI Sample"
            packageVersion = "1.0"
            description = "Jewel New UI Sample Application"
            vendor = "JetBrains"
        }
    }
}

tasks.named<Detekt>("detekt").configure {
    reports {
        sarif.required.set(true)
        sarif.outputLocation.set(file(File(rootDir, "build/reports/detekt-${project.archivesName}.sarif")))
    }
}
