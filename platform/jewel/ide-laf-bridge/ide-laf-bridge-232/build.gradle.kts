plugins {
    jewel
    `jewel-ij-publish`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    api(projects.intUi.intUiCore)
    compileOnly(libs.bundles.idea232)
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    publication.artifactId = "jewel-ide-laf-bridge-platform-specific"
    enabled = supportedIJVersion() == SupportedIJVersion.IJ_232
}