plugins {
    jewel
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    api(projects.intUi.intUiStandalone)
    compileOnly(libs.bundles.idea)

    testImplementation(compose.desktop.uiTestJUnit4)
}
