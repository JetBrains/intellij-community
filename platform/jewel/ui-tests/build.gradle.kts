
import org.jetbrains.compose.ComposeBuildConfig

plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

private val composeVersion
    get() = ComposeBuildConfig.composeVersion

dependencies {
    api(projects.ui)
    api(projects.intUi.intUiStandalone)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
}

