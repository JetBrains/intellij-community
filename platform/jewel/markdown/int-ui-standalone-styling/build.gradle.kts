plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    api(projects.markdown.core)
    api(projects.intUi.intUiStandalone)
    compileOnly(projects.markdown.extensions.gfmAlerts)
    compileOnly(projects.markdown.extensions.gfmTables)

    testImplementation(compose.desktop.uiTestJUnit4)
}
