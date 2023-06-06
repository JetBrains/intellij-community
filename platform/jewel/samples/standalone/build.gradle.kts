import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.jewel.kotlin")
    alias(libs.plugins.composeDesktop)
    id("org.jetbrains.jewel.detekt")
    id("org.jetbrains.jewel.ktlint")
}

dependencies {
    implementation(projects.themes.darcula.darculaStandalone)
    implementation(libs.compose.components.splitpane)
    implementation(projects.jewel.themes.newUi.newUiStandalone)
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
        }
    }
}
