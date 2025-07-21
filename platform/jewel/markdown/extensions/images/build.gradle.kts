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
    runtimeOnly(libs.ktor.client.java)
    testImplementation(compose.desktop.uiTestJUnit4)
}
