plugins {
    id("org.jetbrains.jewel.kotlin")
    alias(libs.plugins.composeDesktop)
    id("org.jetbrains.jewel.detekt")
    id("org.jetbrains.jewel.ktlint")
}

dependencies {
    api(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.jna)
    implementation(libs.kotlinx.serialization.json)
}
