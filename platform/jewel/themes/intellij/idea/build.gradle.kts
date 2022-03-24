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
//    pluginName.set("Compose support for IJ UI development")
    version.set("LATEST-EAP-SNAPSHOT")
    plugins.set(listOf("org.jetbrains.kotlin", "org.jetbrains.compose.desktop.ide:${libs.versions.composeDesktop.get()}"))
    version.set("2021.2.1")
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
