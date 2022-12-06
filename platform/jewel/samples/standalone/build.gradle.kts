import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeDesktop)
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
