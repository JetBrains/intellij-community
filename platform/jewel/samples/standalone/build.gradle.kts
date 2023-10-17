@file:Suppress("UnstableApiUsage")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    jewel
    alias(libs.plugins.composeDesktop)
}

dependencies {
    implementation(projects.intUi.intUiStandalone)
    implementation(projects.intUi.intUiDecoratedWindow)
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
}

compose.desktop {
    application {
        mainClass = "org.jetbrains.jewel.samples.standalone.MainKt"

        jvmArgs("-Dorg.jetbrains.jewel.debug=true")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Jewel Sample"
            packageVersion = "1.0"
            description = "Jewel Sample Application"
            vendor = "JetBrains"
            licenseFile = rootProject.file("LICENSE")

            macOS {
                dockName = "Jewel Sample"
                bundleID = "org.jetbrains.jewel.sample.standalone"
                iconFile = file("icons/jewel.icns")
            }
        }
    }
}
