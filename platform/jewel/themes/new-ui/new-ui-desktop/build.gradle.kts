plugins {
    id("org.jetbrains.jewel.kotlin")
    alias(libs.plugins.composeDesktop)
    id("org.jetbrains.jewel.detekt")
    id("org.jetbrains.jewel.ktlint")
}

dependencies {
    api(projects.themes.newUi.newUiStandalone)
}
