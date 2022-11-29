import org.jetbrains.compose.jetbrainsCompose

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.ideaGradlePlugin)
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
    version.set("223.7571.123-EAP-SNAPSHOT") // IJ 22.3 RC2
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
    implementation(projects.themes.intellij) {
        exclude(compose.desktop.currentOs)
    }
    implementation(projects.library) {
        exclude(compose.desktop.currentOs)
    }
}
