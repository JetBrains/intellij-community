plugins {
    jewel
    `jewel-publish`
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    compileOnly(projects.markdown.core)

    implementation(libs.commonmark.core)

    testImplementation(compose.desktop.uiTestJUnit4)
}

publicApiValidation {
    // We don't foresee changes to the data models for now
    excludedClassRegexes = setOf("org.jetbrains.jewel.markdown.extensions.github.alerts.Alert\\$.*")
}

publishing.publications.named<MavenPublication>("main") {
    artifactId = "jewel-markdown-${project.name}"
}
