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
                jvmTarget = "11"
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
    plugins.set(listOf("org.jetbrains.kotlin", "org.jetbrains.compose.desktop.ide:1.1.1"))
    version.set("2022.1.4")
}

repositories {
    jetbrainsCompose()
    maven("https://androidx.dev/storage/compose-compiler/repository/")
    mavenCentral()
}

dependencies {
    compileOnly(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(projects.themes.intellij) {
        exclude(compose.desktop.currentOs)
    }
    implementation(projects.library) {
        exclude(compose.desktop.currentOs)
    }
}
