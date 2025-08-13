import org.jetbrains.compose.ComposeBuildConfig

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

sourceSets { test { kotlin { srcDirs("src/main/generated-kotlin") } } }

private val composeVersion
    get() = ComposeBuildConfig.composeVersion

dependencies {
    api("org.jetbrains.compose.foundation:foundation-desktop:$composeVersion")

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
}
