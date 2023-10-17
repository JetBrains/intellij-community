plugins {
    jewel
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    api(projects.decoratedWindow)
    api(projects.intUi.intUiStandalone)
}
