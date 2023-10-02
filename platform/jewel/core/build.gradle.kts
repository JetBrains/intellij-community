plugins {
    jewel
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
}
