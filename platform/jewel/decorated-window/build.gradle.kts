import org.jetbrains.compose.ComposeBuildConfig

plugins {
    jewel
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.kotlinSerialization)
}

private val composeVersion get() = ComposeBuildConfig.composeVersion

dependencies {
    api("org.jetbrains.compose.foundation:foundation-desktop:$composeVersion")
    api(projects.core)
    implementation(libs.jna.core)
}
