import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    jewel
    `jewel-publish`
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

publishing.publications.named<MavenPublication>("main") {
    val ijpTarget = project.property("ijp.target") as String
    artifactId = "jewel-markdown-extension-${project.name}-$ijpTarget"
}

composeCompiler { featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups) }
