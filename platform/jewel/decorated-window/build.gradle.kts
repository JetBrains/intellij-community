import org.jetbrains.compose.ComposeBuildConfig

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

private val composeVersion
    get() = ComposeBuildConfig.composeVersion

dependencies {
    api("org.jetbrains.compose.foundation:foundation-desktop:$composeVersion")
    api(projects.intUi.intUiStandalone)
    api(libs.jbr.api)
    implementation(libs.jna.core)
}
