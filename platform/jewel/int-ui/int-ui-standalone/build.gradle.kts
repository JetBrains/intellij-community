plugins {
    jewel
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    api(projects.intUi.intUiCore)
}
