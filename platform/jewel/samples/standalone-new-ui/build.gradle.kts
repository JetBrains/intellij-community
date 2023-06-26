import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    jewel
    alias(libs.plugins.composeDesktop)
}

dependencies {
    implementation(projects.themes.newUi.newUiDesktop)
    implementation(libs.compose.components.splitpane)
}

compose.desktop {
    application {
        mainClass = "org.jetbrains.jewel.samples.standalone.expui.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Jewel New UI Sample"
            packageVersion = "1.0"
            description = "Jewel New UI Sample Application"
            vendor = "JetBrains"
        }
    }
}
