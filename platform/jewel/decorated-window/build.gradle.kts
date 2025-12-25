import org.jetbrains.compose.ComposeBuildConfig

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

private val composeVersion
    get() = ComposeBuildConfig.composeVersion

kotlin {
    sourceSets.named("test") {
        kotlin.srcDir("testSrc")
    }
}

dependencies {
    api("org.jetbrains.compose.foundation:foundation-desktop:$composeVersion")
    api(projects.intUi.intUiStandalone)
    api(libs.jbr.api)
    implementation(libs.jna.core)

    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(kotlin("test"))
}
