plugins {
    jewel
    `jewel-publish`
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    api(projects.ui)
    api(libs.commonmark.core)

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(projects.ui)
    testImplementation(compose.desktop.currentOs)
}

publicApiValidation { excludedClassRegexes = setOf("org.jetbrains.jewel.markdown.MarkdownBlock.*") }

publishing.publications.named<MavenPublication>("main") {
    val ijpTarget = project.property("ijp.target") as String
    artifactId = "jewel-markdown-${project.name}-$ijpTarget"
}
