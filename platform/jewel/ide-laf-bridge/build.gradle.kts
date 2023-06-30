plugins {
    alias(libs.plugins.composeDesktop)
    `jewel-publish`
}

dependencies {
    api(projects.themes.intUi.intUiStandalone)
    compileOnly(libs.bundles.idea)
}
