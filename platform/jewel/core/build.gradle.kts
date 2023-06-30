plugins {
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(projects.composeUtils)
    api(projects.foundation)
    api(compose.desktop.common)
}
