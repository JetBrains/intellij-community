plugins {
    alias(libs.plugins.composeDesktop)
    jewel
}

dependencies {
    api(projects.themes.intUi.intUiStandalone)
    compileOnly(libs.bundles.idea)
}
