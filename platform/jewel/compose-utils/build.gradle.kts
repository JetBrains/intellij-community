plugins {
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    api(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.kotlinx.serialization.json)
}
