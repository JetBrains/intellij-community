import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(projects.markdown.core)

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs)
    testImplementation(projects.markdown.testing)
}

publicApiValidation {
    excludedClassRegexes = setOf("org.jetbrains.jewel.markdown.extensions.frontmatter.*")
}

composeCompiler { featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups) }
