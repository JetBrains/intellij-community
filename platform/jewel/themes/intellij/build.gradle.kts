import org.jetbrains.compose.jetbrainsCompose

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
            languageSettings.optIn("androidx.compose.foundation.ExperimentalFoundationApi")
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
    }
}

repositories {
    jetbrainsCompose()
    maven("https://androidx.dev/storage/compose-compiler/repository/")
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    api(projects.library)
}
