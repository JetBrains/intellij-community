plugins {
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    api(projects.themes.darcula.darculaStandalone)
    compileOnly(libs.bundles.idea)
}
