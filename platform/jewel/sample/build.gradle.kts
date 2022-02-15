import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeDesktop)
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(projects.library)
    implementation(projects.themes.toolbox)
    implementation(projects.themes.intellij.standalone)
}

compose.desktop {
    application {
        mainClass = "org.jetbrains.jewel.sample.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Jewel Sample"
            packageVersion = "1.0"
            description = "Jewel Sample Application"
            vendor = "JetBrains"
        }
    }
}
