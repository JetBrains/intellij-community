plugins {
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    api(projects.themes.intUi.intUiStandalone)
    compileOnly(libs.bundles.idea)
}
