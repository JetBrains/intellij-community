plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(projects.markdown.core)
    implementation(libs.commonmark.ext.autolink)
    testImplementation(compose.desktop.uiTestJUnit4)
}
