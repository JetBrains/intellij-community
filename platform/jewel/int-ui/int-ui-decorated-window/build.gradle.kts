plugins {
    jewel
    `jewel-publish`
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    api(projects.decoratedWindow)
    api(projects.intUi.intUiStandalone)
}
