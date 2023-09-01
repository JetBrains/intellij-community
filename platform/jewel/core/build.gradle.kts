plugins {
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(projects.composeUtils)
    api(compose.desktop.common)
}
