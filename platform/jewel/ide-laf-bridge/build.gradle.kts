import SupportedIJVersion.*

plugins {
    jewel
    `jewel-ij-publish`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    api(projects.intUi.intUiStandalone) {
        exclude(group = "org.jetbrains.kotlinx")
    }
    when (supportedIJVersion()) {
        IJ_232 -> {
            api(projects.ideLafBridge.ideLafBridge232)
            compileOnly(libs.bundles.idea232)
        }

        IJ_233 -> {
            api(projects.ideLafBridge.ideLafBridge233)
            compileOnly(libs.bundles.idea233)
        }
    }

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
}