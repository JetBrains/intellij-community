plugins {
    jewel
    `jewel-publish`
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(projects.markdown.core)
    runtimeOnly(libs.ktor.client.java)
    implementation(libs.coil.compose.core)
    implementation(libs.coil.network.ktor3)
    implementation(libs.coil.svg)
    testImplementation(compose.desktop.uiTestJUnit4)
}

publishing.publications.named<MavenPublication>("main") {
    val ijpTarget = project.property("ijp.target") as String
    artifactId = "jewel-markdown-extension-${project.name}-$ijpTarget"
}

publicApiValidation { excludedClassRegexes = setOf("org.jetbrains.jewel.markdown.extensions.images.*") }
