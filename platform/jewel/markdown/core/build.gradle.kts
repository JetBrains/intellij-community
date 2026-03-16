plugins {
    jewel
    `jewel-check-public-api`
    `java-test-fixtures`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    api(projects.ui)
    api(libs.commonmark.core)
    api(libs.jsoup)

    testFixturesImplementation(projects.foundation)

    testImplementation(testFixtures(project))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(projects.ui)
    testImplementation(compose.desktop.currentOs)
}

publicApiValidation { excludedClassRegexes = setOf("org.jetbrains.jewel.markdown.MarkdownBlock.*") }
