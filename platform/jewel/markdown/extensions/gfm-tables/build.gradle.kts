import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(projects.markdown.core)
    implementation(libs.commonmark.ext.gfm.tables)

    testImplementation(compose.desktop.uiTestJUnit4)
}

publicApiValidation { excludedClassRegexes = setOf("org.jetbrains.jewel.markdown.extensions.github.tables.*") }

composeCompiler { featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups) }
