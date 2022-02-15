plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeDesktop)
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
    api(projects.library)
}
