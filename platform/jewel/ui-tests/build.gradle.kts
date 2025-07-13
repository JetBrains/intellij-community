plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    api(projects.ui)
    api(projects.intUi.intUiStandalone)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
}
