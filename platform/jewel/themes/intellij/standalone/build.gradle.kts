plugins {
    id(libs.plugins.kotlinJvm.get().pluginId)
    id(libs.plugins.composeDesktop.get().pluginId)
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
            }
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    api(projects.themes.intellij)
}
