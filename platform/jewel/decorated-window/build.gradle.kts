import org.jetbrains.compose.ComposeBuildConfig

plugins {
    jewel
    `jewel-publish`
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
}

private val composeVersion
    get() = ComposeBuildConfig.composeVersion

dependencies {
    api("org.jetbrains.compose.foundation:foundation-desktop:$composeVersion")
    api(projects.core)
    implementation(libs.jna.core)
}
