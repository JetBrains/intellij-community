plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    api(projects.intUi.intUiStandalone)
    testImplementation(projects.foundation)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
    testImplementation(libs.jna.core)
}

tasks.test { useJUnitPlatform() }
