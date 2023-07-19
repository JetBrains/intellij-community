import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    jewel
    alias(libs.plugins.composeDesktop)
}

dependencies {
    implementation(projects.themes.intUi.intUiStandalone)
    implementation(libs.compose.components.splitpane)
    implementation(projects.foundation)
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
            licenseFile.set(File(rootDir, "LICENSE"))

            macOS {
                dockName = "Jewel Sample"
                bundleID = "org.jetbrains.jewel.sample.standalone"
                iconFile.set(File(projectDir, "icons/jewel.icns"))
            }
        }
    }
}
