plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    api(libs.coil.compose)
    api(libs.coil.network.ktor3)
    api(libs.coil.svg)

    implementation(projects.markdown.core)
    implementation(projects.ui)
    runtimeOnly(libs.ktor.client.java)
    testImplementation(libs.coil.test)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
    testImplementation(testFixtures(projects.markdown.core))
}
