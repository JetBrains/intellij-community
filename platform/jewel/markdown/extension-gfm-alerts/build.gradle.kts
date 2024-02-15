plugins {
    jewel
    `jewel-publish`
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    // This extension should get all dependencies from ui and markdown-core
    compileOnly(projects.ui)
    compileOnly(projects.markdown.core)
    compileOnly(libs.commonmark.core)

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(projects.markdown.core)
    testImplementation(projects.ui)
}

publicApiValidation {
    // We don't foresee changes to the data models for now
    excludedClassRegexes = setOf("org.jetbrains.jewel.markdown.extensions.github.alerts.Alert\\$.*")
}

publishing.publications.named<MavenPublication>("main") {
    artifactId = "jewel-markdown-${project.name}"
}
