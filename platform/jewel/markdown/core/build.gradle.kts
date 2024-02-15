plugins {
    jewel
    `jewel-publish`
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    compileOnly(projects.ui)
    api(libs.commonmark.core)

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(projects.ui)
}

publicApiValidation {
    // We don't foresee changes to the data models for now
    excludedClassRegexes = setOf("org.jetbrains.jewel.markdown.MarkdownBlock.*")
}

publishing.publications.named<MavenPublication>("main") {
    artifactId = "jewel-markdown-${project.name}"
}
