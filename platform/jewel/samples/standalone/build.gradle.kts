import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.archivesName

plugins {
    id("org.jetbrains.jewel.kotlin")
    alias(libs.plugins.composeDesktop)
    id("org.jetbrains.jewel.detekt")
    id("org.jetbrains.jewel.ktlint")
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
    implementation(projects.themes.darcula.darculaStandalone)
    implementation(libs.compose.components.splitpane)
}

compose.desktop {
    application {
        mainClass = "org.jetbrains.jewel.samples.standalone.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Jewel Sample"
            packageVersion = "1.0"
            description = "Jewel Sample Application"
            vendor = "JetBrains"
        }
    }
}

tasks.named<Detekt>("detekt").configure {
    reports {
        sarif.required.set(true)
        sarif.outputLocation.set(file(rootDir.resolve("build/reports/detekt-${project.archivesName}.sarif")))
    }
}
