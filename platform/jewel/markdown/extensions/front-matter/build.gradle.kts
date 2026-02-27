import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(projects.markdown.core)
    implementation(projects.markdown.extensions.gfmTables)

    testImplementation(compose.desktop.uiTestJUnit4)
}

publicApiValidation {
    excludedClassRegexes = setOf("org.jetbrains.jewel.markdown.extensions.frontmatter.*")
}

composeCompiler { featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups) }
