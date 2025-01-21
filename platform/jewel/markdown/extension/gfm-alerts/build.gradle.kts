plugins {
    jewel
    `jewel-publish`
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(projects.markdown.core)

    testImplementation(compose.desktop.uiTestJUnit4)
}

publicApiValidation {
    // TODO Oleg remove this once migrated to value classes
    excludedClassRegexes = setOf("org.jetbrains.jewel.markdown.extensions.github.alerts.*")
}

publishing.publications.named<MavenPublication>("main") {
    val ijpTarget = project.property("ijp.target") as String
    artifactId = "jewel-markdown-extension-${project.name}-$ijpTarget"
}
